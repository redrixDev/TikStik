// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.core.designsystem.component

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity
import com.redrixdev.tikstik.core.designsystem.theme.Surface1
import com.redrixdev.tikstik.core.designsystem.theme.highFrameRateWhile
import kotlinx.coroutines.launch

/** Only dismissed on purpose; `close` slides it down with its animation and then does what was asked: otherwise it vanished abruptly. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    skipPartiallyExpanded: Boolean = false,
    dismissible: Boolean = true,
    content: @Composable ColumnScope.(close: (then: () -> Unit) -> Unit) -> Unit,
) {
    val canDismiss by rememberUpdatedState(dismissible)
    val scope = rememberCoroutineScope()
    val state = rememberModalBottomSheetState(
        skipPartiallyExpanded = skipPartiallyExpanded,
        confirmValueChange = { it != SheetValue.Hidden || canDismiss },
    )
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = state,
        containerColor = Surface1,
        // Top only: at the bottom the sheet reaches the screen edge and rounded corners showed what was behind.
        shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize),
    ) {
        // Its own window, so its own vote; the empty `scrollable` routes drags to the guard, which drops the upward fling.
        Column(
            Modifier
                .fillMaxWidth()
                .highFrameRateWhile(true)
                .sheetScrollGuard()
                .scrollable(rememberScrollableState { 0f }, Orientation.Vertical),
        ) {
            content { then -> scope.launch { state.hide() }.invokeOnCompletion { if (it == null) then() } }
        }
    }
}

/** If the list moved, the sheet gets neither the leftover scroll nor the fling; an upward fling never: it made the sheet jump. */
@Composable
fun Modifier.sheetScrollGuard(): Modifier {
    val gesture = remember { GestureState() }
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (consumed.y != 0f) gesture.listMoved = true
                return if (gesture.listMoved) available else Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                if (gesture.listMoved || available.y < 0f) available else Velocity.Zero
        }
    }
    return this
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                gesture.listMoved = false
            }
        }
        .nestedScroll(connection)
}

private class GestureState {
    var listMoved = false
}
