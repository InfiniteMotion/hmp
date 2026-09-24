package com.hearablemusic.player.ui.common.navigation

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import androidx.navigation3.runtime.NavKey
import kotlin.test.Test

/**
 * 路由参数传递测试
 * 测试类型安全参数传递和路由序列化
 */
class RoutesTest {

    @Test
    fun `SongDetail route should have correct musicId parameter`() {
        // Given
        val musicId = 123L
        val route = Routes.Library.SongDetail(musicId)

        // When / Then
        assertEquals(musicId, route.musicId)
    }

    @Test
    fun `SongDetail routes with same musicId should be equal`() {
        // Given
        val route1 = Routes.Library.SongDetail(123)
        val route2 = Routes.Library.SongDetail(123)

        // When / Then
        assertEquals(route1, route2)
        assertEquals(route1.hashCode(), route2.hashCode())
    }

    @Test
    fun `SongDetail routes with different musicId should not be equal`() {
        // Given
        val route1 = Routes.Library.SongDetail(123)
        val route2 = Routes.Library.SongDetail(456)

        // When / Then
        assertNotEquals(route1, route2)
    }

    @Test
    fun `Artist route should have correct name parameter`() {
        // Given
        val name = "Artist Name"
        val route = Routes.Library.Artist(name)

        // When / Then
        assertEquals(name, route.name)
    }

    @Test
    fun `Artist routes with same name should be equal`() {
        // Given
        val route1 = Routes.Library.Artist("Artist")
        val route2 = Routes.Library.Artist("Artist")

        // When / Then
        assertEquals(route1, route2)
        assertEquals(route1.hashCode(), route2.hashCode())
    }

    @Test
    fun `Artist routes with different names should not be equal`() {
        // Given
        val route1 = Routes.Library.Artist("Artist1")
        val route2 = Routes.Library.Artist("Artist2")

        // When / Then
        assertNotEquals(route1, route2)
    }

    @Test
    fun `Album route should have correct name parameter`() {
        // Given
        val name = "Album Name"
        val route = Routes.Library.Album(name)

        // When / Then
        assertEquals(name, route.name)
    }

    @Test
    fun `Playlist route should have correct name parameter`() {
        // Given
        val name = "Playlist Name"
        val route = Routes.Playlist.Playlist(name)

        // When / Then
        assertEquals(name, route.name)
    }

    @Test
    fun `CustomPlaylist route should have correct playlistId parameter`() {
        // Given
        val playlistId = 789L
        val route = Routes.Playlist.CustomPlaylist(playlistId)

        // When / Then
        assertEquals(playlistId, route.playlistId)
    }

    @Test
    fun `object routes should be singletons`() {
        // Given
        val route1 = Routes.Main.Tabs
        val route2 = Routes.Main.Tabs

        // When / Then
        assertSame(route1, route2)
    }

    @Test
    fun `different object routes should not be equal`() {
        // Given
        val route1 = Routes.Main.Tabs
        val route2 = Routes.Main.Home

        // When / Then
        assertNotEquals<NavKey>(route1, route2)
    }

    @Test
    fun `SongDetail should serialize and deserialize correctly`() {
        // Given
        val original = Routes.Library.SongDetail(123)

        // When
        val json = Json.encodeToString(original)
        val deserialized = Json.decodeFromString<Routes.Library.SongDetail>(json)

        // Then
        assertEquals(original, deserialized)
        assertEquals(original.musicId, deserialized.musicId)
    }

    @Test
    fun `Artist should serialize and deserialize correctly`() {
        // Given
        val original = Routes.Library.Artist("Artist Name")

        // When
        val json = Json.encodeToString(original)
        val deserialized = Json.decodeFromString<Routes.Library.Artist>(json)

        // Then
        assertEquals(original, deserialized)
        assertEquals(original.name, deserialized.name)
    }

    @Test
    fun `Album should serialize and deserialize correctly`() {
        // Given
        val original = Routes.Library.Album("Album Name")

        // When
        val json = Json.encodeToString(original)
        val deserialized = Json.decodeFromString<Routes.Library.Album>(json)

        // Then
        assertEquals(original, deserialized)
        assertEquals(original.name, deserialized.name)
    }

    @Test
    fun `Playlist should serialize and deserialize correctly`() {
        // Given
        val original = Routes.Playlist.Playlist("Playlist Name")

        // When
        val json = Json.encodeToString(original)
        val deserialized = Json.decodeFromString<Routes.Playlist.Playlist>(json)

        // Then
        assertEquals(original, deserialized)
        assertEquals(original.name, deserialized.name)
    }

    @Test
    fun `CustomPlaylist should serialize and deserialize correctly`() {
        // Given
        val original = Routes.Playlist.CustomPlaylist(789)

        // When
        val json = Json.encodeToString(original)
        val deserialized = Json.decodeFromString<Routes.Playlist.CustomPlaylist>(json)

        // Then
        assertEquals(original, deserialized)
        assertEquals(original.playlistId, deserialized.playlistId)
    }

    @Test
    fun `object route should serialize and deserialize correctly`() {
        // Given
        val original = Routes.Main.Tabs

        // When
        val json = Json.encodeToString(original)
        val deserialized = Json.decodeFromString<Routes.Main.Tabs>(json)

        // Then
        assertEquals(original, deserialized)
    }

    @Test
    fun `all route classes should extend NavKey`() {
        // Given
        val routes = listOf(
            Routes.Main.Tabs,
            Routes.Main.Home,
            Routes.Main.Gallery,
            Routes.Main.List,
            Routes.Main.User,
            Routes.Player.Player,
            Routes.Player.Lyrics,
            Routes.Player.AudioEffects,
            Routes.Library.Search,
            Routes.Library.SongDetail(1),
            Routes.Library.Artist("test"),
            Routes.Library.Album("test"),
            Routes.Playlist.Playlist("test"),
            Routes.Playlist.CustomPlaylist(1),
            Routes.Playlist.UserPlaylistManage,
            Routes.Settings.Setting,
            Routes.Settings.ProfileSettings,
            Routes.Settings.BackupSettings,
            Routes.Settings.LibrarySettings,
            Routes.AI.AI,
            Routes.Custom.Custom,
            Routes.UserData.UserUsageData
        )

        // When / Then
        routes.forEach { route ->
            assertTrue(route is NavKey, "Route $route should be instance of NavKey")
        }
    }

    @Test
    fun `data class routes should have correct toString representation`() {
        // Given
        val songDetail = Routes.Library.SongDetail(123)
        val artist = Routes.Library.Artist("Artist Name")
        val album = Routes.Library.Album("Album Name")
        val playlist = Routes.Playlist.Playlist("Playlist Name")
        val customPlaylist = Routes.Playlist.CustomPlaylist(789)

        // When / Then
        assertTrue(songDetail.toString().contains("123"))
        assertTrue(artist.toString().contains("Artist Name"))
        assertTrue(album.toString().contains("Album Name"))
        assertTrue(playlist.toString().contains("Playlist Name"))
        assertTrue(customPlaylist.toString().contains("789"))
    }
}
