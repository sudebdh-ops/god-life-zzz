# god life zzz

두 사람이 각자의 안드로이드 휴대폰과 PC 크롬에서 사용할 수 있는 루틴 앱입니다.
자체 소스 코드는 MIT 라이선스로 자유롭게 수정·배포할 수 있습니다. 외부 라이브러리와 Android SDK에는 각각의 라이선스가 적용됩니다.
공개 GitHub 저장소: [sudebdh-ops/god-life-zzz](https://github.com/sudebdh-ops/god-life-zzz).

PC에서는 `web` 폴더를 크롬 확장 프로그램으로 로드해 사용하거나 웹 앱으로 실행할 수 있습니다. [PC 설치 안내](web/README.md)를 참고하세요.

## 주요 기능

- 오늘 예정된 루틴 및 완료율 표시
- 루틴 추가·수정·삭제, 사용/일시 중지
- 직접 입력하는 루틴 이름과 메모
- 반복 요일과 알림 시간 설정
- 날짜별 완료 체크 및 예정 요일 기준 연속 달성 횟수
- 공유 공간을 만들고 친구 초대 코드로 두 사람 연결
- 루틴 설정 공유, 완료 기록은 사람별로 분리, 친구의 오늘 완료 표시
- Android·Chrome 기기 간 동기화, 복구 코드로 같은 사람의 새 기기 연결
- 공유하지 않은 기존 루틴은 기기에만 보관; 연결 후 새 루틴은 자동 공유
- 공유하지 않는 동안에는 로컬 기록만으로 사용 가능
- 앱을 닫아도 동작하는 Android 시스템 예약 알림
- 알림에서 바로 완료 체크
- 휴대폰 재부팅, 시간/시간대 변경, 앱 업데이트 후 알림 재예약
- 알림 권한 안내와 테스트 알림
- JSON 파일 백업·가져오기 (같은 ID는 기존 설정 유지 + 완료 이력 합치기)
- 시스템 설정에 맞춘 라이트/다크 테마

사진 인증과 랭킹은 아직 없습니다. JSON 백업은 계속 지원합니다.
동기화를 켜면 인터넷이 필요하며, 공유 루틴의 변경은 서버 성공 후 반영됩니다. Android는 앱이 열려 있는 동안 약 1분마다, Chrome 확장 프로그램은 Chrome 실행 중 약 1분마다 동기화합니다. 일반 웹 버전은 페이지가 열려 있는 동안만 동기화합니다.
복구 코드는 계정 접근 수단이므로 안전하게 보관하세요. 초대 코드와 다릅니다. 친구에게는 초대 코드만 전달하세요.
리마인더는 일반 알림으로 소리·진동을 냅니다. 기상 알람처럼 계속 울리거나 화면을 강제로 켜는 기능은 아직 없습니다.

## 설치와 첫 사용

1. 제공된 APK를 본인 휴대폰에서 엽니다.
2. 파일을 여는 앱에 대해 ‘이 출처 허용’ 또는 ‘알 수 없는 앱 설치’를 허용합니다. 휴대폰/회사 보안 정책에 따라 설치가 제한될 수 있습니다.
3. ‘god life zzz’를 열어 ‘루틴 추가’로 루틴을 만듭니다.
4. 설정에서 알림 권한과 ‘알람 및 리마인더’를 허용합니다.
5. ‘테스트 알림’으로 소리·진동을 확인합니다.
6. 현재보다 2~3분 뒤로 루틴 알림을 설정한 뒤 앱을 닫고 잠금 상태에서 실제 예약 알림도 확인합니다.
7. 둘 중 한 명이 설정에서 **공유 공간 만들기**를 누르고 친구에게 **초대 코드**를 보냅니다. 친구는 **친구 공간 참여**에서 이름과 초대 코드를 입력합니다.
8. 각자 **복구 코드**를 안전하게 저장합니다. 다른 휴대폰이나 Chrome에서 **다른 기기 연결**에 자신의 복구 코드를 넣으면 자신의 완료 기록이 이어집니다.

Android 8.0(API 26) 이상을 지원합니다. 정확한 알람 권한이 없으면 시스템의 부정확한 예약으로 동작해 알림이 늦어질 수 있습니다.
배터리 절약·방해금지·기종별 백그라운드 제한에 영향을 받습니다. 앱 ‘강제 종료’ 후에는 다시 열어야 합니다.
재부팅 후 첫 잠금 해제 시 기록을 읽어 알림을 복구합니다. 이미 지난 시간의 알림을 몰아서 보내지는 않습니다.
완료한 날짜의 루틴은 다시 알리지 않습니다. 같은 날 알림 시간을 바꾸면 새로운 시간에 다시 알릴 수 있습니다.
앱을 삭제하면 이 기기에만 있는 루틴은 사라집니다. 설정에서 먼저 백업하세요. 공유 기록은 복구 코드가 있어야 다시 연결할 수 있습니다.

## 동기화 서버

이 배포본은 Supabase 프로젝트 `nxloiezdoytbukmzftzv`에 연결되어 있습니다. 앱에 포함된 `sb_publishable_...` 키는 공개 클라이언트 키이며 비밀 키가 아닙니다. 원본 프로젝트를 직접 운영하려면 새 Supabase 프로젝트를 만들고 [`supabase/001_shared_routines.sql`](supabase/001_shared_routines.sql)을 실행한 뒤 Authentication → Sign In / Providers에서 anonymous sign-ins를 켜고, `web/sync-config.js`와 `SyncRepository.kt`의 프로젝트 URL·publishable key를 바꾸세요. `service_role` 또는 `sb_secret_` 키를 앱에 넣지 마세요.

서버 데이터는 RLS가 켜진 비공개 스키마에 있고, 익명 기기도 유효한 초대·복구 코드와 서버의 소속 검사 없이는 다른 공간을 읽거나 변경할 수 없습니다. 공유 공간은 최대 두 사람입니다. 현재 앱에는 친구 연결 해제·코드 재발급 UI가 없으므로 코드를 외부에 공개하지 마세요. 무료 Supabase 프로젝트는 장기간 활동이 적으면 일시 중지될 수 있습니다.

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
- 친구 연결 해제·초대 코드 재발급
- 기상 알람 모드, 집중 타이머
- 사진 인증, 위젯

공식 참고: [Compose 설정](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler), [예약 알람](https://developer.android.com/develop/background-work/services/alarms/schedule), [알림 권한](https://developer.android.com/develop/ui/views/notifications/notification-permission), [앱 서명](https://developer.android.com/studio/publish/app-signing).
