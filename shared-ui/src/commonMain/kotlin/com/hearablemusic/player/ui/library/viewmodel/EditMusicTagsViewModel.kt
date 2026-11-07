package com.hearablemusic.player.ui.library.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearablemusic.player.ui.common.util.isFilePathWritable
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.music_not_found
import com.hearablemusic.player.ui.generated.resources.no_changes
import com.hearablemusic.player.ui.generated.resources.unknown_error
import com.hearablemusic.player.ui.platform.MusicWriteAccessResult
import com.hearablemusic.player.ui.platform.PlatformServices
import com.hmp.data.util.MusicTagParser
import com.hmp.domain.music.EditableMusicTags
import com.hmp.domain.music.usecase.EditMusicTagsUseCase
import com.hmp.domain.music.usecase.GetAllMusicUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * UDF 样板屏状态（7.5 P1）：一屏只有这一个不可变状态出口。
 * 异步字段若要 Loading / Empty / Error，用 `LoadState<T>`，不要拿它当整屏状态。
 * 写法约定见 `common/util/UdfConventions.md`。
 */
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
    /** 保存进行中。与其余状态同属一个出口，不另开 StateFlow。 */
    val isSaving: Boolean = false,
    val error: String? = null,
)

/**
 * 一次性效果：toast / 返回 / 错误提示，消费一次即消失，不进 [EditMusicTagsUiState]。
 * 形态同 `DialogManager` 的 `SharedFlow<DialogEvent>`；VM 不持有 NavBackStack / RouteNavigator。
 */
sealed interface EditMusicTagsEffect {
    data object Saved : EditMusicTagsEffect
    data class Failed(val message: String?) : EditMusicTagsEffect
    data object MusicNotFound : EditMusicTagsEffect
}

class EditMusicTagsViewModel(
    private val getAllMusicUseCase: GetAllMusicUseCase,
    private val editMusicTagsUseCase: EditMusicTagsUseCase,
    private val platformServices: PlatformServices,
) : ViewModel() {

    /**
     * 首帧就带上本端的写能力（D2-12）—— 若等到 `loadTags` 才知道，
     * iOS 上会先闪一次可用的保存按钮再收回去。
     */
    private val _uiState = MutableStateFlow(
        EditMusicTagsUiState(canWriteTags = platformServices.musicTagEdit.canWriteTags),
    )
    val uiState: StateFlow<EditMusicTagsUiState> = _uiState.asStateFlow()

    /** 一次性效果通道：与 DialogManager 同配置，缓冲 1 条，消费方还没订阅也不丢。 */
    private val _effect = MutableSharedFlow<EditMusicTagsEffect>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effect: SharedFlow<EditMusicTagsEffect> = _effect.asSharedFlow()

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
        // 重置不能丢掉首帧能力位：canWriteTags 来自平台申报，与加载结果无关
        _uiState.value = EditMusicTagsUiState(
            canWriteTags = platformServices.musicTagEdit.canWriteTags,
        )
        viewModelScope.launch {
            try {
                val musicInfo = getAllMusicUseCase.getMusicById(musicId)
                if (musicInfo == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = getString(Res.string.music_not_found),
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
                        isFileWritable = fileWritable,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: getString(Res.string.unknown_error),
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

    /** 所有字段变更走同一入口，派生字段（hasChanges）在这里重算，不散在各回调里。 */
    private fun updateField(transform: (EditMusicTagsUiState) -> EditMusicTagsUiState) {
        _uiState.update { state ->
            val newState = transform(state)
            newState.copy(hasChanges = computeHasChanges(newState))
        }
    }

    /** 选图是平台副作用，由 VM 发起；回调只把字节写回状态，UI 不直连 PlatformServices。 */
    fun onChooseCover() {
        platformServices.musicTagEdit.pickCoverImage { bytes -> onCoverSelected(bytes) }
    }

    /** 封面字节已由平台服务（pickCoverImage）完成解码/压缩；取消/失败为 null，与旧行为一致不更新状态。 */
    fun onCoverSelected(bytes: ByteArray?) {
        if (bytes != null) {
            updateField { it.copy(newAlbumArtBytes = bytes) }
        }
    }

    fun onRemoveCover() {
        updateField { it.copy(newAlbumArtBytes = ByteArray(0)) }
    }

    fun onRetry() {
        currentMusicId?.let { loadTags(it) }
    }

    /**
     * 保存是唯一的用户动作入口：直写 / 先申请写权限 / 路径缺失，全在 VM 内判定。
     * 平台回调只推进状态或发一次性效果，不把业务结果返回给 UI。
     */
    fun onSaveClick() {
        if (_uiState.value.isFileWritable) {
            save()
            return
        }
        val path = currentMusicPath
        if (path == null) {
            _effect.tryEmit(EditMusicTagsEffect.MusicNotFound)
            return
        }
        platformServices.musicTagEdit.requestMusicWriteAccess(path) { result ->
            when (result) {
                MusicWriteAccessResult.GRANTED -> saveWithSaf()
                MusicWriteAccessResult.DENIED -> {}
                MusicWriteAccessResult.NOT_FOUND -> _effect.tryEmit(EditMusicTagsEffect.MusicNotFound)
            }
        }
    }

    private fun save() {
        val musicId = currentMusicId ?: return
        if (_uiState.value.isSaving || _uiState.value.isLoading) return
        // 本端不支持写标签时这里不该被调到（UI 已收起入口）；这道守卫是防御性的第二层，
        // 不是把失败改成静默 —— 它不发 effect，因此不会出现"点了保存却弹错误"的那种假反馈（D2-12）
        if (!_uiState.value.canWriteTags) return

        viewModelScope.launch {
            val tags = buildTags(_uiState.value) ?: return@launch
            _uiState.update { it.copy(isSaving = true) }
            editMusicTagsUseCase(musicId, tags)
                .onSuccess { _effect.tryEmit(EditMusicTagsEffect.Saved) }
                .onFailure { e ->
                    _effect.tryEmit(
                        EditMusicTagsEffect.Failed(e.message ?: getString(Res.string.unknown_error)),
                    )
                }
            _uiState.update { it.copy(isSaving = false) }
        }
    }

    /**
     * 写权限授权后经 SAF 内容 Uri 写入标签文件，随后仅刷新本地曲库记录。
     * （旧 saveWithUri：Uri 由平台服务按路径反查 MediaStore。）
     */
    private fun saveWithSaf() {
        val musicId = currentMusicId ?: return
        if (_uiState.value.isSaving || _uiState.value.isLoading) return
        if (!_uiState.value.canWriteTags) return

        viewModelScope.launch {
            val tags = buildTags(_uiState.value) ?: return@launch
            _uiState.update { it.copy(isSaving = true) }
            val writeResult = suspendCoroutine { continuation ->
                platformServices.musicTagEdit.writeTagsViaSaf(currentMusicPath, tags) { result ->
                    continuation.resume(result)
                }
            }
            writeResult.fold(
                onSuccess = {
                    editMusicTagsUseCase.refreshAfterFileWrite(musicId, tags)
                        .onSuccess { _effect.tryEmit(EditMusicTagsEffect.Saved) }
                        .onFailure { e ->
                            _effect.tryEmit(
                                EditMusicTagsEffect.Failed(
                                    e.message ?: getString(Res.string.unknown_error),
                                ),
                            )
                        }
                },
                onFailure = { e ->
                    _effect.tryEmit(
                        EditMusicTagsEffect.Failed(
                            e.message ?: getString(Res.string.unknown_error),
                        ),
                    )
                },
            )
            _uiState.update { it.copy(isSaving = false) }
        }
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
            albumArt = state.newAlbumArtBytes,
        )
        if (!tags.hasChanges) {
            _effect.tryEmit(EditMusicTagsEffect.Failed(getString(Res.string.no_changes)))
            return null
        }
        return tags
    }

    private fun computeHasChanges(state: EditMusicTagsUiState): Boolean = (state.title.trim().isNotEmpty() && state.title.trim() != originalTitle) ||
        (state.artist.trim().isNotEmpty() && state.artist.trim() != originalArtist) ||
        (state.album.trim().isNotEmpty() && state.album.trim() != originalAlbum) ||
        (state.year.trim().isNotEmpty() && state.year.trim() != originalYear) ||
        (state.genre.trim().isNotEmpty() && state.genre.trim() != originalGenre) ||
        (state.track.trim().isNotEmpty() && state.track.trim() != originalTrack) ||
        (state.lyrics.isNotEmpty() && state.lyrics != originalLyrics) ||
        state.newAlbumArtBytes != null

    private companion object {
        const val MAX_YEAR_LENGTH = 4
        const val MAX_TRACK_LENGTH = 3
    }
}
