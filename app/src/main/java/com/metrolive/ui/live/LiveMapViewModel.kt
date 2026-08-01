package com.metrolive.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolive.data.BoardingPosition
import com.metrolive.data.MetroRepository
import com.metrolive.data.Train
import com.metrolive.data.TrainCongestion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LiveUiState(
    val line: String = "1호선",
    val upLine: Boolean = true,
    val trains: List<Train> = emptyList(),
    val selectedTrainNo: String? = null,
    val secondsSinceRefresh: Int = 0,
    val apiError: String? = null,
    val congestion: TrainCongestion? = null,
    val showBoardingSheet: Boolean = false,
    val boardingForTrip: Boolean = false,
    val boarding: BoardingPosition? = null,
) {
    val selectedTrain get() = trains.firstOrNull { it.trainNo == selectedTrainNo }
}

class LiveMapViewModel(
    private val repo: MetroRepository = MetroRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(LiveUiState())
    val state: StateFlow<LiveUiState> = _state.asStateFlow()
    private var pollJob: Job? = null

    init {
        restartPolling()
        viewModelScope.launch { // 1초 틱: 카운트다운 & 갱신경과
            while (true) {
                delay(1000)
                _state.value = _state.value.copy(
                    secondsSinceRefresh = _state.value.secondsSinceRefresh + 1,
                    trains = _state.value.trains.map {
                        if (it.etaSeconds > 0) it.copy(etaSeconds = it.etaSeconds - 1) else it
                    },
                )
            }
        }
    }

    private fun restartPolling() {
        pollJob?.cancel()
        val s = _state.value
        pollJob = viewModelScope.launch {
            repo.liveTrains(s.line, s.upLine).collect { trains ->
                _state.value = _state.value.copy(
                    trains = trains,
                    secondsSinceRefresh = 0,
                    apiError = repo.lastError,
                    selectedTrainNo = _state.value.selectedTrainNo
                        ?.takeIf { no -> trains.any { it.trainNo == no } }
                )
            }
        }
    }

    fun setLineDirection(line: String, up: Boolean) {
        _state.value = _state.value.copy(line = line, upLine = up, trains = emptyList(), selectedTrainNo = null)
        restartPolling()
    }

    fun selectLine(line: String) {
        if (line == _state.value.line) return
        _state.value = _state.value.copy(line = line, trains = emptyList(), selectedTrainNo = null)
        restartPolling()
    }

    fun setDirection(up: Boolean) {
        if (up == _state.value.upLine) return
        _state.value = _state.value.copy(upLine = up, trains = emptyList(), selectedTrainNo = null)
        restartPolling()
    }

    fun selectTrain(trainNo: String) {
        _state.value = _state.value.copy(
            selectedTrainNo = if (_state.value.selectedTrainNo == trainNo) null else trainNo)
    }

    fun openCongestion() {
        val t = _state.value.selectedTrain ?: return
        _state.value = _state.value.copy(congestion = repo.congestion(t.trainNo))
    }
    fun closeCongestion() { _state.value = _state.value.copy(congestion = null) }
    fun requestBoard(forTrip: Boolean = true) {
        _state.value = _state.value.copy(showBoardingSheet = true, boardingForTrip = forTrip)
    }
    fun confirmBoarding(pos: BoardingPosition?) {
        _state.value = _state.value.copy(showBoardingSheet = false, boarding = pos)
    }
}
