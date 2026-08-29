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
            // Two levels of PrabhixOperator: the project directory, then the source directory Xcode
            // creates inside it. The shallower path silently resolves to nothing.
            path: "PrabhixOperator/PrabhixOperator/Core"
        ),
    ]
)
