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
import androidx.compose.ui.window.ComposeUIViewController
import anystream.ui.App
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice
import platform.UIKit.UIInterfaceOrientation
import platform.UIKit.UIInterfaceOrientationPortrait

@Suppress("FunctionName") // called from Swift
fun MainViewController() = ComposeUIViewController {
//    UIInterfaceOrientation currentOrientation = [UIApplication sharedApplication].statusBarOrientation;
//    NSNumber *value = [NSNumber numberWithInt:UIInterfaceOrientationPortrait];
//    [[UIDevice currentDevice] setValue:value forKey:@"orientation"];
//    [UIViewController attemptRotationToDeviceOrientation];
    App {
        val currentOrientation = UIApplication.sharedApplication.statusBarOrientation
        val num = UIInterfaceOrientationPortrait

        UIDevice.currentDevice.orientation()
//        currenDec
    }
}
