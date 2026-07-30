package com.example.screens

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.DecimalFormat

enum class Operation(val symbol: String, val label: String) {
    ADD("+", "Addition"),
    SUBTRACT("-", "Subtraction"),
    MULTIPLY("×", "Multiplication"),
    DIVIDE("÷", "Division")
}

data class CalculationHistory(
    val id: Long = System.currentTimeMillis(),
    val num1: String,
    val num2: String,
    val operation: Operation,
    val result: String,
    val formula: String
)

data class CalculatorUiState(
    val firstNumber: String = "",
    val secondNumber: String = "",
    val firstNumberError: String? = null,
    val secondNumberError: String? = null,
    val selectedOperation: Operation? = null,
    val result: String? = null,
    val calculationFormula: String? = null,
    val history: List<CalculationHistory> = emptyList()
)

sealed interface UiEvent {
    data class ShowSnackbar(val message: String) : UiEvent
}

class CalculatorViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CalculatorUiState())
    val uiState: StateFlow<CalculatorUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    private val decimalFormatter = DecimalFormat("#,##0.########")

    fun onFirstNumberChange(value: String) {
        val sanitized = sanitizeNumericInput(value)
        _uiState.update {
            it.copy(
                firstNumber = sanitized,
                firstNumberError = null
            )
        }
        recalculateIfPossible()
    }

    fun onSecondNumberChange(value: String) {
        val sanitized = sanitizeNumericInput(value)
        _uiState.update {
            it.copy(
                secondNumber = sanitized,
                secondNumberError = null
            )
        }
        recalculateIfPossible()
    }

    private fun sanitizeNumericInput(input: String): String {
        val clean = input.trim()
        if (clean.isEmpty()) return ""
        val hasMinus = clean.startsWith("-")
        val withoutMinus = if (hasMinus) clean.substring(1) else clean
        val parts = withoutMinus.split(".")
        return if (parts.size > 2) {
            val whole = parts[0]
            val decimal = parts.subList(1, parts.size).joinToString("")
            (if (hasMinus) "-" else "") + "$whole.$decimal"
        } else {
            clean
        }
    }

    fun selectOperationAndCalculate(op: Operation) {
        _uiState.update { it.copy(selectedOperation = op) }
        calculateResult(op, isExplicitAction = true)
    }

    private fun recalculateIfPossible() {
        val currentOp = _uiState.value.selectedOperation
        if (currentOp != null && _uiState.value.firstNumber.isNotBlank() && _uiState.value.secondNumber.isNotBlank()) {
            calculateResult(currentOp, isExplicitAction = false)
        }
    }

    private fun calculateResult(op: Operation, isExplicitAction: Boolean) {
        val state = _uiState.value
        val num1Text = state.firstNumber.trim()
        val num2Text = state.secondNumber.trim()

        var num1Error: String? = null
        var num2Error: String? = null

        if (num1Text.isEmpty()) {
            num1Error = "First number is required"
        }
        if (num2Text.isEmpty()) {
            num2Error = "Second number is required"
        }

        val d1 = num1Text.toDoubleOrNull()
        if (num1Text.isNotEmpty() && d1 == null) {
            num1Error = "Invalid decimal number"
        }

        val d2 = num2Text.toDoubleOrNull()
        if (num2Text.isNotEmpty() && d2 == null) {
            num2Error = "Invalid decimal number"
        }

        if (num1Error != null || num2Error != null) {
            _uiState.update {
                it.copy(
                    firstNumberError = num1Error,
                    secondNumberError = num2Error,
                    result = null,
                    calculationFormula = null
                )
            }
            if (isExplicitAction) {
                val errorMsg = when {
                    num1Error != null && num2Error != null -> "Please enter both numbers"
                    num1Error != null -> num1Error
                    else -> num2Error ?: "Invalid input"
                }
                _uiEvent.tryEmit(UiEvent.ShowSnackbar(errorMsg))
            }
            return
        }

        val val1 = d1!!
        val val2 = d2!!

        if (op == Operation.DIVIDE && val2 == 0.0) {
            _uiState.update {
                it.copy(
                    secondNumberError = "Cannot divide by zero",
                    result = null,
                    calculationFormula = "${formatNumber(val1)} ÷ 0"
                )
            }
            _uiEvent.tryEmit(UiEvent.ShowSnackbar("Error: Division by zero is not allowed!"))
            return
        }

        val rawResult = when (op) {
            Operation.ADD -> val1 + val2
            Operation.SUBTRACT -> val1 - val2
            Operation.MULTIPLY -> val1 * val2
            Operation.DIVIDE -> val1 / val2
        }

        val formattedResult = formatNumber(rawResult)
        val formula = "${formatNumber(val1)} ${op.symbol} ${formatNumber(val2)}"

        val newHistory = if (isExplicitAction) {
            val item = CalculationHistory(
                num1 = num1Text,
                num2 = num2Text,
                operation = op,
                result = formattedResult,
                formula = formula
            )
            listOf(item) + state.history.take(9)
        } else {
            state.history
        }

        _uiState.update {
            it.copy(
                firstNumberError = null,
                secondNumberError = null,
                result = formattedResult,
                calculationFormula = formula,
                history = newHistory
            )
        }
    }

    private fun formatNumber(number: Double): String {
        return if (number == number.toLong().toDouble()) {
            number.toLong().toString()
        } else {
            decimalFormatter.format(number)
        }
    }

    fun clearAll() {
        _uiState.update {
            CalculatorUiState(history = it.history)
        }
    }

    fun clearHistory() {
        _uiState.update { it.copy(history = emptyList()) }
    }

    fun loadHistoryItem(item: CalculationHistory) {
        _uiState.update {
            it.copy(
                firstNumber = item.num1,
                secondNumber = item.num2,
                selectedOperation = item.operation,
                result = item.result,
                calculationFormula = item.formula,
                firstNumberError = null,
                secondNumberError = null
            )
        }
    }

    fun swapNumbers() {
        val current = _uiState.value
        _uiState.update {
            it.copy(
                firstNumber = current.secondNumber,
                secondNumber = current.firstNumber,
                firstNumberError = null,
                secondNumberError = null
            )
        }
        recalculateIfPossible()
    }
}
