import Flutter
import Photos
import UIKit

@main
@objc class AppDelegate: FlutterAppDelegate, FlutterImplicitEngineDelegate {
  override func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
  ) -> Bool {
    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }

  func didInitializeImplicitFlutterEngine(_ engineBridge: FlutterImplicitEngineBridge) {
    GeneratedPluginRegistrant.register(with: engineBridge.pluginRegistry)
    // Copies a photo taken in the app into the Photos app, in a "Daily Work Log" album.
    if let registrar = engineBridge.pluginRegistry.registrar(forPlugin: "DailyWorkLogGallery") {
      let channel = FlutterMethodChannel(name: "dailyworklog/gallery", binaryMessenger: registrar.messenger())
      channel.setMethodCallHandler { call, result in
        guard call.method == "save",
              let args = call.arguments as? [String: Any],
              let path = args["path"] as? String else {
          result(FlutterMethodNotImplemented)
          return
        }
        let album = args["album"] as? String ?? "Daily Work Log"
        GallerySaver.save(path: path, album: album) { ok in
          DispatchQueue.main.async { result(ok) }
        }
      }
    }
  }
}

/// Saves a separate copy, so deleting it in Photos leaves the app's photo alone (and the other way round).
enum GallerySaver {
  static func save(path: String, album: String, done: @escaping (Bool) -> Void) {
    let url = URL(fileURLWithPath: path)
    PHPhotoLibrary.requestAuthorization(for: .readWrite) { status in
      guard status == .authorized || status == .limited else {
        done(false)
        return
      }
      // With limited access the album can't be found or made; the photo still goes in Recents.
      albumNamed(album, allowed: status == .authorized) { collection in
        PHPhotoLibrary.shared().performChanges({
          guard let request = PHAssetChangeRequest.creationRequestForAssetFromImage(atFileURL: url) else { return }
          if let collection = collection,
             let placeholder = request.placeholderForCreatedAsset,
             let add = PHAssetCollectionChangeRequest(for: collection) {
            add.addAssets([placeholder] as NSArray)
          }
        }) { ok, _ in done(ok) }
      }
    }
  }

  private static func albumNamed(_ name: String, allowed: Bool, then: @escaping (PHAssetCollection?) -> Void) {
    guard allowed else {
      then(nil)
      return
    }
    let options = PHFetchOptions()
    options.predicate = NSPredicate(format: "title = %@", name)
    if let found = PHAssetCollection.fetchAssetCollections(with: .album, subtype: .any, options: options).firstObject {
      then(found)
      return
    }
    var id: String?
    PHPhotoLibrary.shared().performChanges({
      id = PHAssetCollectionChangeRequest.creationRequestForAssetCollection(withTitle: name)
        .placeholderForCreatedAssetCollection.localIdentifier
    }) { ok, _ in
      guard ok, let id = id else {
        then(nil)
        return
      }
      then(PHAssetCollection.fetchAssetCollections(withLocalIdentifiers: [id], options: nil).firstObject)
    }
  }
}
