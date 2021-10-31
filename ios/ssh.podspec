#
# To learn more about a Podspec see http://guides.cocoapods.org/syntax/podspec.html
#
Pod::Spec.new do |s|
  s.name             = 'ssh'
  s.version          = '1.0.0'
  s.summary          = 'SSH and SFTP client for Flutter.'
  s.description      = <<-DESC
SSH and SFTP client for Flutter. Wraps iOS library NMSSH and Android library Jsch.
                       DESC
  s.homepage         = 'https://github.com/bogkonstantin/flutter_ssh'
  s.license          = { :file => '../LICENSE' }
  s.author           = { 'Konstantin Bogomolov' => 'https://github.com/bogkonstantin' }
  s.source           = { :path => '.' }
  s.source_files = 'Classes/**/*'
  s.public_header_files = 'Classes/**/*.h'
  s.dependency 'Flutter'
  s.dependency 'NMSSH'
  
  s.ios.deployment_target = '8.0'
end

