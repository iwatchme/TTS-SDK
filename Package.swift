// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "TtsSdk",
    platforms: [
        .iOS(.v14),
    ],
    products: [
        .library(
            name: "TtsSdk",
            targets: ["TtsSdk"]
        ),
    ],
    targets: [
        .binaryTarget(
            name: "TtsSdk",
            path: "tts-player/build/XCFrameworks/release/TtsSdk.xcframework"
        ),
    ]
)
