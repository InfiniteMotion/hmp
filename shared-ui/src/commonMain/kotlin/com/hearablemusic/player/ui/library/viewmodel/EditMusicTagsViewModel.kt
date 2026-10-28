package com.hearablemusic.player.ui.library.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmp.data.util.MusicTagParser
import com.hmp.domain.music.EditableMusicTags
import com.hmp.domain.music.usecase.EditMusicTagsUseCase
import com.hmp.domain.music.usecase.GetAllMusicUseCase
import com.hearablemusic.player.ui.common.util.isFilePathWritable
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.music_not_found
import com.hearablemusic.player.ui.generated.resources.no_changes
import com.hearablemusic.player.ui.generated.resources.unknown_error
import com.hearablemusic.player.ui.platform.MusicWriteAccessResult
import com.hearablemusic.player.ui.platform.PlatformServices
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString

data class EditMusicTagsUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val year: String = "",
    val genre: String = "",
    val track: String = "",
    val lyrics: String = "",
    val albumArtUri: String? = null,
    /** 新选择的封面字节；空数组表示移除封面，null 表示不修改 */
    val newAlbumArtBytes: ByteArray? = null,
    /** 是否有未保存的更改 */
    val hasChanges: Boolean = false,
    /** 音乐文件是否可直接写入；不可写时需通过 SAF 授权后保存 */
    val isFileWritable: Boolean = false,
    /**
     * 本端有没有写标签的能力（D2-12）。false 时表单只读、保存按钮不出现 ——
     * 由 `MusicTagEditService.canWriteTags` 申报，不是 UI 自己去猜平台。
     */
    val canWriteTags: Boolean = true,
    val error: String? = null
)

sealed interface SaveTagsResult {
    data object Success : SaveTagsResult
    data class Error(val message: String?) : SaveTagsResult
}

class EditMusicTagsViewModel(
    private val getAllMusicUseCase: GetAllMusicUseCase,
    private val editMusicTagsUseCase: EditMusicTagsUseCase,
    private val platformServices: PlatformServices
) : ViewModel() {

    /**
     * 首帧就带上本端的写能力（D2-12）—— 若等到 `loadTags` 才知道，
     * iOS 上会先闪一次可用的保存按钮再收回去。
     */
    private val _uiState = MutableStateFlow(
        EditMusicTagsUiState(canWriteTags = platformServices.musicTagEdit.canWriteTags)
    )
    val uiState: StateFlow<EditMusicTagsUiState> = _uiState.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveResult = MutableStateFlow<SaveTagsResult?>(null)
    val saveResult: StateFlow<SaveTagsResult?> = _saveResult.asStateFlow()

    private var currentMusicId: Long? = null
    private var currentMusicPath: String? = null
    private var originalTitle = ""
    private var originalArtist = ""
    private var originalAlbum = ""
    private var originalYear = ""
    private var originalGenre = ""
    private var originalTrack = ""
    private var originalLyrics = ""

    fun loadTags(musicId: Long) {
        currentMusicId = musicId
        _uiState.value = EditMusicTagsUiState()
        viewModelScope.launch {
            try {
                val musicInfo = getAllMusicUseCase.getMusicById(musicId)
                if (musicInfo == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = getString(Res.string.music_not_found)
                        )
                    }
                    return@launch
                }
                val music = musicInfo.music
                currentMusicPath = music.path
                val lyrics = musicInfo.extra?.lyrics.orEmpty()
                originalTitle = music.title
                originalArtist = music.artist
                originalAlbum = music.album
                originalLyrics = lyrics

                // 年份/流派/曲目号不在数据库，从文件标签解析（可能为空）
                var year = ""
                var genre = ""
                var track = ""
                var fileWritable = false
                withContext(Dispatchers.Default) {
                    fileWritable = isFilePathWritable(music.path)
                    MusicTagParser.parseMetadata(music.path)?.let { meta ->
                        year = meta.year.orEmpty()
                        genre = meta.genre.orEmpty()
                        track = meta.track.orEmpty()
                    }
                }
                originalYear = year
                originalGenre = genre
                originalTrack = track

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        title = music.title,
                        artist = music.artist,
                        album = music.album,
                        year = year,
                        genre = genre,
                        track = track,
                        lyrics = lyrics,
                        albumArtUri = music.albumArtUri,
                        hasChanges = false,
                        isFileWritable = fileWritable
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: getString(Res.string.unknown_error)
                    )
                }
            }
        }
    }

    fun onTitleChange(value: String) = updateField { it.copy(title = value) }
    fun onArtistChange(value: String) = updateField { it.copy(artist = value) }
    fun onAlbumChange(value: String) = updateField { it.copy(album = value) }
    fun onYearChange(value: String) {
        val filtered = value.filter { it.isDigit() }.take(MAX_YEAR_LENGTH)
        updateField { it.copy(year = filtered) }
    }
    fun onGenreChange(value: String) = updateField { it.copy(genre = value) }
    fun onTrackChange(value: String) {
        val filtered = value.filter { it.isDigit() }.take(MAX_TRACK_LENGTH)
        updateField { it.copy(track = filtered) }
    }
    fun onLyricsChange(value: String) = updateField { it.copy(lyrics = value) }

    private fun updateField(transform: (EditMusicTagsUiState) -> EditMusicTagsUiState) {
        _uiState.update { state ->
            val newState = transform(state)
            newState.copy(hasChanges = computeHasChanges(newState))
        }
    }

    /** 封面字节已由平台服务（pickCoverImage）完成解码/压缩；取消/失败为 null，与旧行为一致不更新状态。 */
    fun onCoverSelected(bytes: ByteArray?) {
        if (bytes != null) {
            _uiState.update { it.copy(newAlbumArtBytes = bytes) }
        }
    }

    fun onRemoveCover() {
        updateField { it.copy(newAlbumArtBytes = ByteArray(0)) }
    }

    fun retry() {
        currentMusicId?.let { loadTags(it) }
    }

    /**
     * 对当前音乐文件发起写权限请求（旧 resolveWriteUri + MediaStore.createWriteRequest 流程收口进平台服务）。
     * 路径缺失时与旧行为一致：视为未找到（music_not_found 提示场景）。
     */
    fun requestWriteAccess(onResult: (MusicWriteAccessResult) -> Unit) {
        val path = currentMusicPath
        if (path == null) {
            onResult(MusicWriteAccessResult.NOT_FOUND)
            return
        }
        platformServices.musicTagEdit.requestMusicWriteAccess(path, onResult)
    }

    fun save() {
        val musicId = currentMusicId ?: return
        if (_isSaving.value || _uiState.value.isLoading) return
        // 本端不支持写标签时这里不该被调到（UI 已收起入口）；这道守卫是防御性的第二层，
        // 不是把失败改成静默 —— 它不写 _saveResult，因此不会出现"点了保存却弹错误"的那种假反馈（D2-12）
        if (!_uiState.value.canWriteTags) return

        viewModelScope.launch {
            val tags = buildTags(_uiState.value) ?: return@launch
            _isSaving.value = true
            editMusicTagsUseCase(musicId, tags)
                .onSuccess {
                    _saveResult.value = SaveTagsResult.Success
                }
                .onFailure { e ->
                    _saveResult.value = SaveTagsResult.Error(
                        e.message ?: getString(Res.string.unknown_error)
                    )
                }
            _isSaving.value = false
        }
    }

    /**
     * 写权限授权后经 SAF 内容 Uri 写入标签文件，随后仅刷新本地曲库记录。
     * （旧 saveWithUri：Uri 由平台服务按路径反查 MediaStore。）
     */
    fun saveAfterWriteAccessGranted() {
        val musicId = currentMusicId ?: return
        if (_isSaving.value || _uiState.value.isLoading) return
        if (!_uiState.value.canWriteTags) return

        viewModelScope.launch {
            val tags = buildTags(_uiState.value) ?: return@launch
            _isSaving.value = true
            val writeResult = suspendCoroutine { continuation ->
                platformServices.musicTagEdit.writeTagsViaSaf(currentMusicPath, tags) { result ->
                    continuation.resume(result)
                }
            }
            writeResult.fold(
                onSuccess = {
                    editMusicTagsUseCase.refreshAfterFileWrite(musicId, tags)
                        .onSuccess {
                            _saveResult.value = SaveTagsResult.Success
                        }
                        .onFailure { e ->
                            _saveResult.value = SaveTagsResult.Error(
                                e.message
                                    ?: getString(Res.string.unknown_error)
                            )
                        }
                },
                onFailure = { e ->
                    _saveResult.value = SaveTagsResult.Error(
                        e.message ?: getString(Res.string.unknown_error)
                    )
                }
            )
            _isSaving.value = false
        }
    }

    fun clearSaveResult() {
        _saveResult.value = null
    }

    private suspend fun buildTags(state: EditMusicTagsUiState): EditableMusicTags? {
        val tags = EditableMusicTags(
            title = state.title.trim().takeIf { it.isNotEmpty() && it != originalTitle },
            artist = state.artist.trim().takeIf { it.isNotEmpty() && it != originalArtist },
            album = state.album.trim().takeIf { it.isNotEmpty() && it != originalAlbum },
            year = state.year.trim().takeIf { it.isNotEmpty() && it != originalYear },
            genre = state.genre.trim().takeIf { it.isNotEmpty() && it != originalGenre },
            track = state.track.trim().takeIf { it.isNotEmpty() && it != originalTrack },
            lyrics = state.lyrics.takeIf { it.isNotEmpty() && it != originalLyrics },
            albumArt = state.newAlbumArtBytes
        )
        if (!tags.hasChanges) {
            _saveResult.value = SaveTagsResult.Error(
                getString(Res.string.no_changes)
            )
            return null
        }
        return tags
    }

    private fun computeHasChanges(state: EditMusicTagsUiState): Boolean {
        return (state.title.trim().isNotEmpty() && state.title.trim() != originalTitle) ||
            (state.artist.trim().isNotEmpty() && state.artist.trim() != originalArtist) ||
            (state.album.trim().isNotEmpty() && state.album.trim() != originalAlbum) ||
            (state.year.trim().isNotEmpty() && state.year.trim() != originalYear) ||
            (state.genre.trim().isNotEmpty() && state.genre.trim() != originalGenre) ||
            (state.track.trim().isNotEmpty() && state.track.trim() != originalTrack) ||
            (state.lyrics.isNotEmpty() && state.lyrics != originalLyrics) ||
            state.newAlbumArtBytes != null
    }

    private companion object {
        const val MAX_YEAR_LENGTH = 4
        const val MAX_TRACK_LENGTH = 3
    }
}