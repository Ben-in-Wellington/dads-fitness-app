// feature_audio/src/main/java/com/di/feature_audio/RadioViewModel.kt

package com.di.feature_audio

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class RadioViewModel @Inject constructor(
    private val repo: RadioRepository
) : ViewModel() {

    /* expose the repository flow – no manual writes anymore */
    val isPlaying: StateFlow<Boolean> = repo.isPlaying

    fun toggle(station: RadioStation) {
        if (repo.isPlaying.value) repo.stop() else repo.play(station)
        // no local state mutation – repository will emit the real value
    }

    fun stopIfPlaying() = repo.stop()
}