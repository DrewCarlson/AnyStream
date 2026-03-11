/**
 * AnyStream
 * Copyright (C) 2021 Drew Carlson
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
import java.io.File
import org.gradle.api.Project

private var sdkFound: Boolean? = null

val Project.hasAndroidSdk: Boolean
    get() {
        if (sdkFound != null) {
            return sdkFound!!
        }
        val localProps = File(rootDir, "local.properties")
        if (localProps.exists() && localProps.readText().contains("sdk.dir")) {
            val sdkDir = localProps.readLines().firstNotNullOfOrNull {
                it.substringAfter("sdk.dir=", "")
                    .takeUnless(String::isEmpty)
            }
            if (sdkDir != null) {
                logger.lifecycle("[AnyStream Meta] Found SDK dir in local.properties: $sdkDir")
                sdkFound = true
                return true
            }
        }
        val trySdkDir = sequenceOf(
            System.getProperty("user.home") + "/Library",
            System.getenv("LOCALAPPDATA")
        ).firstNotNullOfOrNull {
            File("$it/Android/sdk")
                .takeIf(File::exists)
        } ?: System.getenv("ANDROID_HOME")?.let { androidHome ->
            File(androidHome)
                .takeIf(File::exists)
        }
        sdkFound = if (trySdkDir?.exists() == true) {
            val pathString = trySdkDir.absolutePath
                .replace("\\", "\\\\")
                .replace(":", "\\:")
            logger.lifecycle("[AnyStream Meta] Writing SDK dir to local.properties: $pathString")
            localProps.appendText("\nsdk.dir=$pathString")
            true
        } else {
            logger.lifecycle("[AnyStream Meta] Android SDK is not installed")
            false
        }
        return sdkFound!!
    }
