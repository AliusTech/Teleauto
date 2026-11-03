#
# To learn more about a Podspec see http://guides.cocoapods.org/syntax/podspec.html.
# Run `pod lib lint teleauto_core.podspec` to validate before publishing.
#
Pod::Spec.new do |s|
  s.name             = 'teleauto_core'
  s.version          = '0.1.0'
  s.summary          = 'Teleauto SDK platform adapter.'
  s.description      = <<-DESC
Teleauto SDK platform adapter.
                       DESC
  s.homepage         = 'https://github.com/AliusTech/dji_rc'
  s.license          = { :file => '../LICENSE' }
  s.author           = 'AliusTech'
  s.source           = { :path => '.' }
  s.source_files = 'teleauto_core/Sources/teleauto_core/**/*'
  s.dependency 'Flutter'
  s.platform = :ios, '13.0'

  # Flutter.framework does not contain a i386 slice.
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES', 'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'i386' }
  s.swift_version = '5.0'

  # If your plugin requires a privacy manifest, for example if it uses any
  # required reason APIs, update the PrivacyInfo.xcprivacy file to describe your
  # plugin's privacy impact, and then uncomment this line. For more information,
  # see https://developer.apple.com/documentation/bundleresources/privacy_manifest_files
  # s.resource_bundles = {'teleauto_core_privacy' => ['teleauto_core/Sources/teleauto_core/PrivacyInfo.xcprivacy']}
end
