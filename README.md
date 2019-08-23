# Nifi

## 프로세서 커스텀 하는 방법 

(https://bhjo0930.tistory.com/entry/Nifi-Custom-Processor-%EB%A7%8C%EB%93%A4%EA%B8%B0 참고)

1) mvn archetype:generate
2) Choose a number or apply filter : nifi (입력)
3) Choose a number or apply filter : remote -> org.apache.nifi:nifi-processor-bundle-archetype 번호 (입력)
4) 버전선택
5) groupid, artifactid, version, artifactBaseName, package 원하는대로 입력
6) 최종 Y 결정 (N입력시 groupid 부터 새로 작성)
7) 이클립스 기준 Existing Maven Project 로 만들어진 프로젝트 불러오기
8) 메인프로젝트, nifi-*-processors, nifi-*-nar 가 불려옴
9) 서비스수정은 nifi-com-processors 에서 작성 
10) 메인파일 변경시 /src/main/resources/META-INF/service 에 메인파일을 바꿔줘야함 (이후는 응용)
11) 빌드 패키징은 processors > 메인프로젝트 > nar 순으로 최종결과물은 nar의 target에 떨어짐

## NIFI 자체 로그관리

/conf/logback.xml
에서 아파치 로그파일관련된 설정을 할수 있습니다.

## 1. 프로시저 호출하는 SQL서비스 (CallProcedureSQL)

### 사용법) 

1. CallProcedureSQL 컴포넌트를 호출한다.
2. (args.... , varchar outMsg1) 을 기준으로 만들어졌다.
3. SQL에 프로시저명(${변수명1}, ${변수명2}, ....., ${변수명n), ?) 형식으로 작성하면 된다.
4. 정상작동하게되면 attribute 값으로 outMsg1 가 출력된다.



## 2. 소켓통신 서비스 (CallSocket)

### 사용법) 

1. CallSocket 컴포넌트를 호출한다.
2. ip, port를 입력하고 전송할 data를 입력한뒤 사용한다.
