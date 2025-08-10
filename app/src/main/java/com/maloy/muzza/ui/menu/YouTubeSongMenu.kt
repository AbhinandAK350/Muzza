package com.maloy.muzza.ui.menu

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.maloy.innertube.YouTube
import com.maloy.innertube.models.SongItem
import com.maloy.muzza.LocalDatabase
import com.maloy.muzza.LocalDownloadUtil
import com.maloy.muzza.LocalPlayerConnection
import com.maloy.muzza.R
import com.maloy.muzza.constants.ListItemHeight
import com.maloy.muzza.constants.ListThumbnailSize
import com.maloy.muzza.constants.ThumbnailCornerRadius
import com.maloy.muzza.db.entities.SongEntity
import com.maloy.muzza.extensions.toMediaItem
import com.maloy.muzza.models.MediaMetadata
import com.maloy.muzza.models.toMediaMetadata
import com.maloy.muzza.playback.ExoDownloadService
import com.maloy.muzza.playback.queues.YouTubeQueue
import com.maloy.muzza.ui.component.DownloadListMenu
import com.maloy.muzza.ui.component.ListMenu
import com.maloy.muzza.ui.component.ListMenuItem
import com.maloy.muzza.ui.component.ListDialog
import com.maloy.muzza.ui.component.ListItem
import com.maloy.muzza.utils.joinByBullet
import com.maloy.muzza.utils.makeTimeString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@Composable
fun YouTubeSongMenu(
    song: SongItem,
    navController: NavController,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val librarySong by database.song(song.id).collectAsState(initial = null)
    val download by LocalDownloadUtil.current.getDownload(song.id).collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()
    val artists = remember {
        song.artists.mapNotNull {
            it.id?.let { artistId ->
                MediaMetadata.Artist(id = artistId, name = it.name)
            }
        }
    }

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    AddToPlaylistDialog(
        navController = navController,
        isVisible = showChoosePlaylistDialog,
        onGetSong = { playlist ->
            database.transaction {
                insert(song.toMediaMetadata())
            }

            coroutineScope.launch(Dispatchers.IO) {
                playlist.playlist.browseId?.let { browseId ->
                    YouTube.addToPlaylist(browseId, song.id)
                }
            }

            listOf(song.id)
        },
        onDismiss = { showChoosePlaylistDialog = false }
    )

    var showSelectArtistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showSelectArtistDialog) {
        ListDialog(
            onDismiss = { showSelectArtistDialog = false }
        ) {
            items(artists) { artist ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .height(ListItemHeight)
                        .clickable {
                            navController.navigate("artist/${artist.id}")
                            showSelectArtistDialog = false
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .height(ListItemHeight)
                            .clickable {
                                navController.navigate("artist/${artist.id}")
                                showSelectArtistDialog = false
                                onDismiss()
                            }
                            .padding(horizontal = 24.dp),
                    ) {
                        Text(
                            text = artist.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    ListItem(
        title = song.title,
        subtitle = joinByBullet(
            song.artists.joinToString { it.name },
            song.duration?.let { makeTimeString(it * 1000L) }
        ),
        thumbnailContent = {
            AsyncImage(
                model = song.thumbnail,
                contentDescription = null,
                modifier = Modifier
                    .size(ListThumbnailSize)
                    .clip(RoundedCornerShape(ThumbnailCornerRadius))
            )
        },
        trailingContent = {
            IconButton(
                onClick = {
                    database.transaction {
                        librarySong.let { librarySong ->
                            if (librarySong == null) {
                                insert(song.toMediaMetadata(), SongEntity::toggleLike)
                            } else {
                                update(librarySong.song.toggleLike())
                            }
                        }
                    }
                }
            ) {
                Icon(
                    painter = painterResource(if (librarySong?.song?.liked == true) R.drawable.favorite else R.drawable.favorite_border),
                    tint = if (librarySong?.song?.liked == true) MaterialTheme.colorScheme.error else LocalContentColor.current,
                    contentDescription = null
                )
            }
        }
    )
    HorizontalDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                )
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
                    onDismiss()
                }
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(R.drawable.radio),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringResource(R.string.start_radio),
                style = MaterialTheme.typography.labelMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .basicMarquee()
                    .padding(top = 4.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                )
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    showChoosePlaylistDialog = true
                }
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(R.drawable.playlist_add),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringResource(R.string.add_to_playlist),
                style = MaterialTheme.typography.labelMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .basicMarquee()
                    .padding(top = 4.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                )
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    val intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, song.shareLink)
                    }
                    context.startActivity(Intent.createChooser(intent, null))
                    onDismiss()
                }
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(R.drawable.share),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringResource(R.string.share),
                style = MaterialTheme.typography.labelMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .basicMarquee()
                    .padding(top = 4.dp),
            )
        }
    }

    ListMenu(
        contentPadding = PaddingValues(
            start = 8.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
        )
    ) {
        ListMenuItem(
            icon = R.drawable.playlist_play,
            title = R.string.play_next
        ) {
            playerConnection.playNext(song.toMediaItem())
            onDismiss()
        }
        item {
            HorizontalDivider()
        }
        ListMenuItem(
            icon = R.drawable.queue_music,
            title = R.string.add_to_queue
        ) {
            playerConnection.addToQueue((song.toMediaItem()))
            onDismiss()
        }
        item {
            HorizontalDivider()
        }
        if (librarySong?.song?.inLibrary != null) {
            ListMenuItem(
                icon = R.drawable.library_add_check,
                title = R.string.remove_from_library
            ) {
                database.query {
                    inLibrary(song.id, null)
                }
            }
            item {
                HorizontalDivider()
            }
        } else {
            ListMenuItem(
                icon = R.drawable.library_add,
                title = R.string.add_to_library
            ) {
                database.transaction {
                    insert(song.toMediaMetadata())
                    inLibrary(song.id, LocalDateTime.now())
                }
            }
            item {
                HorizontalDivider()
            }
        }
        DownloadListMenu(
            state = download?.state,
            onDownload = {
                database.transaction {
                    insert(song.toMediaMetadata())
                }
                val downloadRequest = DownloadRequest.Builder(song.id, song.id.toUri())
                    .setCustomCacheKey(song.id)
                    .setData(song.title.toByteArray())
                    .build()
                DownloadService.sendAddDownload(
                    context,
                    ExoDownloadService::class.java,
                    downloadRequest,
                    false
                )
            },
            onRemoveDownload = {
                DownloadService.sendRemoveDownload(
                    context,
                    ExoDownloadService::class.java,
                    song.id,
                    false
                )
            }
        )
        item {
            HorizontalDivider()
        }
        if (artists.isNotEmpty()) {
            ListMenuItem(
                icon = R.drawable.artist,
                title = R.string.view_artist
            ) {
                if (artists.size == 1) {
                    navController.navigate("artist/${artists[0].id}")
                    onDismiss()
                } else {
                    showSelectArtistDialog = true
                }
            }
            item {
                HorizontalDivider()
            }
        }
        song.album?.let { album ->
            ListMenuItem(
                icon = R.drawable.album,
                title = R.string.view_album
            ) {
                navController.navigate("album/${album.id}")
                onDismiss()
            }
            item {
                HorizontalDivider()
            }
        }
        ListMenuItem(
            icon = R.drawable.music_note,
            title = R.string.listen_youtube_music
        ) {
            val intent = Intent(Intent.ACTION_VIEW, song.shareLink.toUri())
            context.startActivity(intent)
        }
    }
}
