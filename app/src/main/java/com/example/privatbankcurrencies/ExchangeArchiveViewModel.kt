package com.example.privatbankcurrencies

import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

sealed interface ExchangeArchiveUiState {
    data object Initial : ExchangeArchiveUiState
    data object Loading : ExchangeArchiveUiState
    data class Success(
        val responseDate: String,
        val allRates: List<ExchangeRateDto>,
        val displayedRates: List<ExchangeRateDto>
    ) : ExchangeArchiveUiState
    data class Error(val message: String) : ExchangeArchiveUiState
}

class ExchangeArchiveViewModel : ViewModel() {

    private val api = PrivatBankNetworkModule.api

    private val _selectedDate = MutableStateFlow(DateUtils.todayString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortAlphabetically = MutableStateFlow(false)
    val sortAlphabetically: StateFlow<Boolean> = _sortAlphabetically.asStateFlow()

    private val _uiState = MutableStateFlow<ExchangeArchiveUiState>(ExchangeArchiveUiState.Initial)
    val uiState: StateFlow<ExchangeArchiveUiState> = _uiState.asStateFlow()

    private var currentCall: Call<ExchangeArchiveResponse>? = null

    fun onDateChanged(newDate: String) {
        if (_uiState.value is ExchangeArchiveUiState.Loading) return
        _selectedDate.value = newDate
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        recalculateDisplayedRates()
    }

    fun onToggleSort() {
        _sortAlphabetically.value = !_sortAlphabetically.value
        recalculateDisplayedRates()
    }

    fun loadRates() {
        if (_uiState.value is ExchangeArchiveUiState.Loading) return

        val dateToFetch = _selectedDate.value.trim()
        val validationError = DateUtils.validateArchiveDate(dateToFetch)
        if (validationError != null) {
            _uiState.value = ExchangeArchiveUiState.Error(validationError)
            return
        }

        currentCall?.cancel()
        _uiState.value = ExchangeArchiveUiState.Loading

        Log.d("PrivatBankApi", "Початок мережевого запиту для дати: $dateToFetch")

        val call = api.getArchiveRates(date = dateToFetch)
        currentCall = call

        call.enqueue(object : Callback<ExchangeArchiveResponse> {
            override fun onResponse(
                call: Call<ExchangeArchiveResponse>,
                response: Response<ExchangeArchiveResponse>
            ) {
                if (call !== currentCall || call.isCanceled) return

                if (!response.isSuccessful) {
                    _uiState.value = ExchangeArchiveUiState.Error(
                        "Сервер повернув помилку HTTP ${response.code()}. Спробуйте пізніше."
                    )
                    return
                }

                val body = response.body()
                if (body == null) {
                    _uiState.value = ExchangeArchiveUiState.Error(
                        "Сервер повернув порожню відповідь."
                    )
                    return
                }

                val validRates = body.exchangeRate.orEmpty()
                    .filter { !it.currency.isNullOrBlank() }

                val loadedDate = body.date?.takeIf { it.isNotBlank() } ?: dateToFetch

                _uiState.value = ExchangeArchiveUiState.Success(
                    responseDate = loadedDate,
                    allRates = validRates,
                    displayedRates = filterAndSort(validRates)
                )
            }

            override fun onFailure(call: Call<ExchangeArchiveResponse>, t: Throwable) {
                if (call !== currentCall || call.isCanceled) return

                _uiState.value = ExchangeArchiveUiState.Error(
                    "Не вдалося завантажити курси. Перевірте з'єднання та спробуйте знову."
                )
            }
        })
    }

    private fun recalculateDisplayedRates() {
        val currentState = _uiState.value
        if (currentState is ExchangeArchiveUiState.Success) {
            _uiState.value = currentState.copy(
                displayedRates = filterAndSort(currentState.allRates)
            )
        }
    }

    private fun filterAndSort(rates: List<ExchangeRateDto>): List<ExchangeRateDto> {
        val query = _searchQuery.value.trim()
        val filtered = if (query.isEmpty()) {
            rates
        } else {
            rates.filter { it.currency?.contains(query, ignoreCase = true) == true }
        }

        return if (_sortAlphabetically.value) {
            filtered.sortedBy { it.currency.orEmpty() }
        } else {
            filtered
        }
    }

    override fun onCleared() {
        currentCall?.cancel()
        super.onCleared()
    }
}