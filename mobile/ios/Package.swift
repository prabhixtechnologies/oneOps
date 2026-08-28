// swift-tools-version: 5.10
import PackageDescription

let package = Package(
    name: "PrabhixOperatorCore",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "PrabhixOperatorCore", targets: ["PrabhixOperatorCore"]),
    ],
    targets: [
        .target(
            name: "PrabhixOperatorCore",
            path: "PrabhixOperator/Core"
        ),
    ]
)
