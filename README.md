# god life zzz

두 사람이 각자의 안드로이드 휴대폰과 PC 크롬에서 사용할 수 있는 루틴 앱의 첫 버전입니다.
자체 소스 코드는 MIT 라이선스로 자유롭게 수정·배포할 수 있습니다. 외부 라이브러리와 Android SDK에는 각각의 라이선스가 적용됩니다.
공개 GitHub 저장소: [sudebdh-ops/god-life-zzz](https://github.com/sudebdh-ops/god-life-zzz).

PC에서는 `web` 폴더를 크롬 확장 프로그램으로 로드해 사용하거나 웹 앱으로 실행할 수 있습니다. [PC 설치 안내](web/README.md)를 참고하세요.

## 첫 버전에 들어있는 기능

- 오늘 예정된 루틴 및 완료율 표시
- 루틴 추가·수정·삭제, 사용/일시 중지
- 직접 입력하는 루틴 이름과 메모
- 반복 요일과 알림 시간 설정
- 날짜별 완료 체크 및 예정 요일 기준 연속 달성 횟수
- 휴대폰에만 기록 저장, 인터넷·회원가입·서버 불필요
- 앱을 닫아도 동작하는 Android 시스템 예약 알림
- 알림에서 바로 완료 체크
- 휴대폰 재부팅, 시간/시간대 변경, 앱 업데이트 후 알림 재예약
- 알림 권한 안내와 테스트 알림
- JSON 파일 백업·가져오기 (같은 ID는 기존 설정 유지 + 완료 이력 합치기)
- 시스템 설정에 맞춘 라이트/다크 테마

친구끼리 기록 공유, 사진 인증, 랭킹, 로그인은 후속 확장 범위입니다. 현재 두 기기의 기록은 서로 독립적입니다.
Android와 크롬 버전 사이에서는 같은 JSON 백업 파일로 기록을 옮길 수 있습니다.
리마인더는 일반 알림으로 소리·진동을 냅니다. 기상 알람처럼 계속 울리거나 화면을 강제로 켜는 기능은 아직 없습니다.

## 설치와 첫 사용

1. 제공된 APK를 본인 휴대폰에서 엽니다.
2. 파일을 여는 앱에 대해 ‘이 출처 허용’ 또는 ‘알 수 없는 앱 설치’를 허용합니다. 휴대폰/회사 보안 정책에 따라 설치가 제한될 수 있습니다.
3. ‘god life zzz’를 열어 ‘루틴 추가’로 루틴을 만듭니다.
4. 설정에서 알림 권한과 ‘알람 및 리마인더’를 허용합니다.
5. ‘테스트 알림’으로 소리·진동을 확인합니다.
6. 현재보다 2~3분 뒤로 루틴 알림을 설정한 뒤 앱을 닫고 잠금 상태에서 실제 예약 알림도 확인합니다.

Android 8.0(API 26) 이상을 지원합니다. 정확한 알람 권한이 없으면 시스템의 부정확한 예약으로 동작해 알림이 늦어질 수 있습니다.
배터리 절약·방해금지·기종별 백그라운드 제한에 영향을 받습니다. 앱 ‘강제 종료’ 후에는 다시 열어야 합니다.
재부팅 후 첫 잠금 해제 시 기록을 읽어 알림을 복구합니다. 이미 지난 시간의 알림을 몰아서 보내지는 않습니다.
완료한 날짜의 루틴은 다시 알리지 않습니다. 같은 날 알림 시간을 바꾸면 새로운 시간에 다시 알릴 수 있습니다.
앱을 삭제하면 기록이 사라집니다. 설정에서 먼저 백업하세요.

## 개발 환경과 빌드

- Android Studio 또는 JDK 17 + Android SDK
- Kotlin 2.1.20, Jetpack Compose, Material 3
- Android Gradle Plugin 8.9.2, Gradle 8.11.1
- compile/target SDK 35, Build Tools 35.0.0

Android Studio에서 이 폴더를 Open한 뒤 Gradle 동기화를 기다리면 됩니다. SDK 설치 경로는 IDE가 `local.properties`에 설정합니다.
명령줄에서는 `JAVA_HOME`과 `ANDROID_HOME`을 설치 경로로 지정한 뒤 실행합니다.

Windows:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

macOS/Linux:

```sh
sh ./gradlew testDebugUnitTest lintDebug assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

개인 테스트용 APK는 debug 서명입니다. 같은 APK를 두 기기에 설치하는 것은 가능합니다.
이 작업 폴더에서 생성된 debug 키는 `.local-signing/debug.keystore`에 보관되며 소스 ZIP/Git에서는 제외됩니다.
다른 PC에서 재빌드하거나 키를 삭제하면 debug 서명 키가 달라져 덮어 설치가 실패할 수 있습니다.
업데이트를 계속 공유하려면 고정된 개인 릴리스 키를 생성하고 Android Studio의 **Build → Generate Signed App Bundle / APK → APK**에서 서명하세요.
서명 키와 암호는 따로 안전하게 보관하고 Git에 올리지 마세요. 제공된 APK와 릴리스 APK의 키가 다르면 기존 앱을 백업 후 삭제하고 처음 설치해야 합니다.

## 코드 구조

```text
app/src/main/java/dev/gatsaeng/app/
├── MainActivity.kt             권한·설정 이동·파일 가져오기/내보내기
├── RoutineViewModel.kt         화면 상태, 루틴 변경, 백업 합치기
├── model/Routine.kt            루틴 모델, 다음 알림 계산, 연속 달성
├── data/
│   ├── RoutineCodec.kt         버전이 있는 JSON 직렬화·검증
│   └── RoutineRepository.kt    휴대폰 로컬 저장
├── notifications/
│   ├── AlarmScheduler.kt       시스템 예약 알람·알림 채널
│   └── Receivers.kt            알람 수신·완료 액션·재부팅 복구
└── ui/
    ├── Theme.kt                색상·다크 모드
    ├── GatsaengApp.kt          오늘·내 루틴·설정 화면
    └── RoutineEditor.kt        루틴 편집 화면
```

처음에는 비어 있는 상태로 시작합니다. 샘플 루틴은 강제로 추가하지 않습니다.
요일을 바꾸면 연속 달성 횟수는 현재 반복 요일을 기준으로 다시 계산합니다. 날짜별 완료 기록은 유지됩니다.
저장 형식을 확장할 때는 기존 `version: 1` 데이터를 읽는 마이그레이션을 추가하세요.

## 이어서 확장할 수 있는 것

- 사용자 정의 루틴 분류와 목표량
- 달력·주간 통계
- 친구 연결과 공유용 저장소
- 기상 알람 모드, 집중 타이머
- 사진 인증, 위젯

공식 참고: [Compose 설정](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler), [예약 알람](https://developer.android.com/develop/background-work/services/alarms/schedule), [알림 권한](https://developer.android.com/develop/ui/views/notifications/notification-permission), [앱 서명](https://developer.android.com/studio/publish/app-signing).
