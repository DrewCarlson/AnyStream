/**
 * AnyStream
 * Copyright (C) 2023 AnyStream Maintainers
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
package anystream.ui.video

import com.sun.jna.NativeLibrary
import uk.co.caprica.vlcj.binding.lib.LibC
import uk.co.caprica.vlcj.binding.support.runtime.RuntimeUtil
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import java.io.File

public fun prepareLibvlc() {
    try {
        prepareInternalLibvlc()
    } catch (e: Throwable) {
        // Fallback to system library linkage
        NativeDiscovery().discover()
    }
}

private fun prepareInternalLibvlc() {
    val resourceDir = File(System.getProperty("compose.application.resources.dir"))
    val targetFolder = resourceDir.resolve("libvlc")
    val pluginsFolder = targetFolder.resolve("plugins")

    LibC.INSTANCE.setenv("VLC_PLUGIN_PATH", pluginsFolder.absolutePath, 1)

    // Note: It is required to load libvlccore first to ensure that when libvlc
    // is loaded, there is no error when the system tries to load libvlccore.
    NativeLibrary.addSearchPath(
        RuntimeUtil.getLibVlcCoreLibraryName(),
        targetFolder.absolutePath,
    )
    NativeLibrary.getInstance(RuntimeUtil.getLibVlcCoreLibraryName())

    NativeLibrary.addSearchPath(
        RuntimeUtil.getLibVlcLibraryName(),
        targetFolder.absolutePath,
    )
}
