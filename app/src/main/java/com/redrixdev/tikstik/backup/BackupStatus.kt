// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.backup

import kotlinx.serialization.Serializable

/** What the Google account or the Drive backup is doing. */
enum class BackupWork { CONNECTING, BACKING_UP, RESTORING }

/** Why the backup could not be made. */
@Serializable
enum class BackupProblem { NO_NETWORK, SIGN_IN, DRIVE_FULL, NEWER_VERSION, FAILED }
