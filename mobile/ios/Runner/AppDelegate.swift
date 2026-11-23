import EventKit
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
    // Link reminders in the Calendar app.
    if let registrar = engineBridge.pluginRegistry.registrar(forPlugin: "DailyWorkLogCalendar") {
      let channel = FlutterMethodChannel(name: "dailyworklog/calendar", binaryMessenger: registrar.messenger())
      channel.setMethodCallHandler { call, result in
        let args = call.arguments as? [String: Any] ?? [:]
        switch call.method {
        case "save": CalendarReminders.save(args, result: result)
        case "delete": CalendarReminders.delete(args, result: result)
        default: result(FlutterMethodNotImplemented)
        }
      }
    }
  }
}

/// Link reminders written straight into the Calendar app, after asking once for access.
enum CalendarReminders {
  static let store = EKEventStore()

  private static func access(_ done: @escaping (Bool) -> Void) {
    if #available(iOS 17.0, *) {
      store.requestFullAccessToEvents { ok, _ in done(ok) }
    } else {
      store.requestAccess(to: .event) { ok, _ in done(ok) }
    }
  }

  static func save(_ args: [String: Any], result: @escaping FlutterResult) {
    access { ok in
      DispatchQueue.main.async {
        guard ok else {
          result(FlutterError(code: "denied", message: nil, details: nil))
          return
        }
        let id = args["eventId"] as? String ?? ""
        let event = (id.isEmpty ? nil : store.event(withIdentifier: id)) ?? EKEvent(eventStore: store)
        if event.calendar == nil {
          guard let calendar = store.defaultCalendarForNewEvents else {
            result(FlutterError(code: "no_calendar", message: nil, details: nil))
            return
          }
          event.calendar = calendar
        }
        let start = Date(timeIntervalSince1970: ((args["start"] as? NSNumber)?.doubleValue ?? 0) / 1000)
        event.title = args["title"] as? String ?? ""
        event.notes = args["notes"] as? String
        if let link = args["url"] as? String { event.url = URL(string: link) }
        event.timeZone = TimeZone.current
        event.startDate = start
        event.endDate = start.addingTimeInterval(30 * 60)
        let frequency: EKRecurrenceFrequency? = {
          switch args["repeat"] as? String ?? "" {
          case "daily": return .daily
          case "weekly": return .weekly
          case "monthly": return .monthly
          case "yearly": return .yearly
          default: return nil
          }
        }()
        event.recurrenceRules = frequency.map { [EKRecurrenceRule(recurrenceWith: $0, interval: 1, end: nil)] }
        let minutes = (args["alerts"] as? [NSNumber])?.map { $0.doubleValue }
          ?? [(args["alert"] as? NSNumber)?.doubleValue ?? 0]
        event.alarms = minutes.map { EKAlarm(relativeOffset: -$0 * 60) }
        do {
          try store.save(event, span: .futureEvents, commit: true)
          result(event.eventIdentifier)
        } catch {
          result(FlutterError(code: "failed", message: error.localizedDescription, details: nil))
        }
      }
    }
  }

  static func delete(_ args: [String: Any], result: @escaping FlutterResult) {
    let id = args["eventId"] as? String ?? ""
    guard !id.isEmpty, let event = store.event(withIdentifier: id) else {
      result(false)
      return
    }
    do {
      try store.remove(event, span: .futureEvents, commit: true)
      result(true)
    } catch {
      result(false)
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
