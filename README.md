## react-native-videoplayer

基于react-native-video开发的react-native平台视频播放器，完美支持`Android` `iOS`，**包含全屏功能**

Requires react-native >= 0.41.0

### 安装

在cmd中运行 `npm install react-native-videoplayer`

### Linking

#### 自动
Run `react-native link`

#### 手动

##### iOS

**AppDelegate.m**

```objective-c
#import <AVFoundation/AVFoundation.h>  // import

- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)launchOptions
{
  ...
  [[AVAudioSession sharedInstance] setCategory:AVAudioSessionCategoryAmbient error:nil];  
  ...
}
```

##### Android

**android/settings.gradle**

```
include ':react-native-videoplayer'
project(':react-native-videoplayer').projectDir = new File(rootProject.projectDir, '../node_modules/react-native-videoplayer/android')
```

**android/app/build.gradle**

```
dependencies {
   ...
   compile project(':react-native-videoplayer')
}
```

**MainActivity.java**

On top, where imports are:

```java
import com.brentvatne.react.ReactVideoPackage;
```

Under `.addPackage(new MainReactPackage())`:

```java
.addPackage(new ReactVideoPackage())
```

### Note: In react-native >= 0.41.0 you have to edit `MainApplication.java`

**MainApplication.java** (react-native >= 0.41.0)

On top, where imports are:

```java
import com.brentvatne.react.ReactVideoPackage;
```

Under `.addPackage(new MainReactPackage())`:

```java
.addPackage(new ReactVideoPackage())
```

## Usage

```javascript
// Within your render function, assuming you have a file called
// "background.mp4" in your project. You can include multiple videos
// on a single screen if you like.

<Video source={{uri: "background"}}   // Can be a URL or a local file.
       ref={(ref) => {
         this.player = ref
       }}                             // Store reference
       rate={1.0}                     // 0 is paused, 1 is normal.
       volume={1.0}                   // 0 is muted, 1 is normal.
       muted={false}                  // Mutes the audio entirely.
       paused={false}                 // Pauses playback entirely.
       resizeMode="cover"             // Fill the whole screen at aspect ratio.
       repeat={true}                  // Repeat forever.
       playInBackground={false}       // Audio continues to play when app entering background.
       playWhenInactive={false}       // [iOS] Video continues to play when control or notification center are shown.
       progressUpdateInterval={250.0} // [iOS] Interval to fire onProgress (default to ~250ms)
       onLoadStart={this.loadStart}   // Callback when video starts to load
       onLoad={this.setDuration}      // Callback when video loads
       onProgress={this.setTime}      // Callback every ~250ms with currentTime
       onEnd={this.onEnd}             // Callback when playback finishes
       onError={this.videoError}      // Callback when video cannot be loaded
       onBuffer={this.onBuffer} // Callback when remote video is buffering
       style={styles.backgroundVideo} />

// Later to trigger fullscreen
this.player.presentFullscreenPlayer()

// To set video position in seconds (seek)
this.player.seek(0)

// Later on in your styles..
var styles = StyleSheet.create({
  backgroundVideo: {
    position: 'absolute',
    top: 0,
    left: 0,
    bottom: 0,
    right: 0,
  },
});
```


## Android Expansion File Usage

```javascript
// Within your render function, assuming you have a file called
// "background.mp4" in your expansion file. Just add your main and (if applicable) patch version
<Video source={{uri: "background", mainVer: 1, patchVer: 0}} // Looks for .mp4 file (background.mp4) in the given expansion version.
       rate={1.0}                   // 0 is paused, 1 is normal.
       volume={1.0}                 // 0 is muted, 1 is normal.
       muted={false}                // Mutes the audio entirely.
       paused={false}               // Pauses playback entirely.
       resizeMode="cover"           // Fill the whole screen at aspect ratio.
       repeat={true}                // Repeat forever.
       onLoadStart={this.loadStart} // Callback when video starts to load
       onLoad={this.setDuration}    // Callback when video loads
       onProgress={this.setTime}    // Callback every ~250ms with currentTime
       onEnd={this.onEnd}           // Callback when playback finishes
       onError={this.videoError}    // Callback when video cannot be loaded
       style={styles.backgroundVideo} />

// Later on in your styles..
var styles = Stylesheet.create({
  backgroundVideo: {
    position: 'absolute',
    top: 0,
    left: 0,
    bottom: 0,
    right: 0,
  },
});
```

**MIT Licensed**
