package com.metrolive.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolive.data.BoardingPosition
import com.metrolive.data.MetroRepository
import com.metrolive.data.Train
import com.metrolive.data.TrainCongestion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LiveUiState(
    val trains: List<Train> = emptyList(),
    val selectedTrainNo: String? = null,          // 열차 변경 = 카드 탭 (C4)
    val secondsSinceRefresh: Int = 0,
    val congestion: TrainCongestion? = null,      // null 이면 시트 닫힘
    val showBoardingSheet: Boolean = false,
    val boarding: BoardingPosition? = null,
    val upLine: Boolean = true,                   // 내선/성수 방면
) {
    val selectedTrain get() = trains.firstOrNull { it.trainNo == selectedTrainNo }
}

class LiveMapViewModel(
    private val repo: MetroRepository = MetroRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(LiveUiState())
    val state: StateFlow<LiveUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.liveTrains("2호선", baseStation = "시청", upLine = true).collect { trains ->
                _state.value = _state.value.copy(
                    trains = trains,
                    secondsSinceRefresh = 0,
                    // 추천 = ETA 최소인 탑승 가능 열차. 사용자가 이미 고른 열차가 살아있으면 유지
                    selectedTrainNo = _state.value.selectedTrainNo
                        ?.takeIf { no -> trains.any { it.trainNo == no } }
                        ?: trains.filter { it.etaSeconds >= 0 }.minByOrNull { it.etaSeconds }?.trainNo,
                )
            }
        }
        viewModelScope.launch { // 갱신 경과/카운트다운 1초 틱
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

    /** C4: 노선도 위 열차 카드 탭 → 선택 전환 */
    fun selectTrain(trainNo: String) {
        _state.value = _state.value.copy(selectedTrainNo = trainNo)
    }

    /** C3: 혼잡도 보기 */
    fun openCongestion() {
        val t = _state.value.selectedTrain ?: return
        _state.value = _state.value.copy(congestion = repo.congestion(t.trainNo))
    }

    fun closeCongestion() { _state.value = _state.value.copy(congestion = null) }

    /** 탑승 시작 → 탑승 위치 입력 시트 (C1) */
    fun requestBoard() { _state.value = _state.value.copy(showBoardingSheet = true) }

    fun confirmBoarding(pos: BoardingPosition?) {
        _state.value = _state.value.copy(showBoardingSheet = false, boarding = pos)
    }
}
