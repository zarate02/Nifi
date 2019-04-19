# Nifi

## 1. 프로시저 호출하는 SQL서비스 (callprocedure)

### 사용법) 

1. CallProcedureSQL 컴포넌트를 호출한다.
2. (args.... , cursor, varchar outMsg1, varchar outMsg2) 를 기준으로 만들어졌다.
3. SQL에 프로시저명(${변수명1}, ${변수명2}, ....., ${변수명n), ?,?,?) 형식으로 작성하면 된다.
4. 정상작동하게되면 attribute 값으로 outMsg1, outMsg2 가 출력된다.
