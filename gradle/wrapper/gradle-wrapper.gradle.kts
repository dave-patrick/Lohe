// Declarative wrapper. In Gradle 9 this file is the source of truth and
// gradle-wrapper.properties is generated from it — an earlier version pinned
// 9.3.1 here while the properties said 9.6.0, so the two disagreed and only
// one of them decided what actually ran.
//
// 9.6.0 is the version the build is verified against: 199 unit tests and a
// clean assembleDebug on AGP 9.4.1 / Kotlin 2.3.21.
//
// BIN rather than ALL: the sources and docs in the full distribution are
// megabytes nobody on a build machine reads.
tasks.register("wrapper", Wrapper::class) {
    gradleVersion = "9.6.0"
    distributionType = Wrapper.DistributionType.BIN
}