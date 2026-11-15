# 배포 사이트의 사용자 화면 주소는 React가 쓰고, 서버 렌더링 화면은 로컬에서만 연다

사용자 화면을 React(`commerce-front`)로 새로 만들면서 Thymeleaf 사용자 화면은 지우지 않고 남긴다. 나중에 JSP로 바꿔 보며 공부하려고 남기는 것이다. 배포 사이트에서는 nginx가 `/api`, `/oauth2`, `/login`, `/logout`, `/admin`, `/uploads`만 Spring으로 보내고, 그 밖의 화면 주소(`/`, `/cart`, `/products/{id}` 등)는 모두 React로 보낸다. 그래서 Thymeleaf(나중에 JSP) 사용자 화면은 코드와 경로가 그대로 있어도 배포 사이트에서는 열리지 않는다. 로컬에서 Spring 포트(8080, compose는 8082)로 직접 접속할 때만 열린다. React와 Spring은 같은 출처로 두어 세션 쿠키 인증과 CSRF를 그대로 쓴다.

## Considered Options

- **React를 `/shop/*` 아래에 두기**: 두 화면을 모두 서비스할 수 있지만, 서버 렌더링 화면은 공부용이라 그럴 필요가 없다. React 주소만 길어진다.
- **React가 `/`를 쓰고 Thymeleaf를 `/legacy/*`로 옮기기**: Thymeleaf 컨트롤러와 링크를 전부 고쳐야 한다.
- **nginx에 접두어를 붙여 서버 렌더링 화면으로 보내기(`/legacy/cart` → `/cart`)**: 화면 안의 절대 경로 링크, 폼 action, 리다이렉트가 접두어 없이 React로 빠져나간다.
- **서브도메인으로 나누기**: 출처가 달라져 CORS, 쿠키, CSRF 설정을 다시 잡아야 한다.

## Consequences

- 로그인 성공 후 이동은 React에서 시작한 로그인과 서버 렌더링 화면에서 시작한 로그인을 구분해야 한다. React는 돌아갈 경로를 넘기고, 백엔드는 `/`로 시작하는 사이트 안의 경로만 받는다.
- React는 로그인 화면(`/login`)을 거치지 않고 `/oauth2/authorization/naver?returnTo=<경로>`로 바로 보낸다. 로그인 화면은 받은 `returnTo`를 네이버 로그인 링크에 이어 붙이지 않으므로, `/login?returnTo=`로 보내면 로그인 뒤 홈으로 간다.
- nginx를 거치지 않고 접속하기 때문에, 로컬의 서버 렌더링 화면은 blue/green 전환의 영향을 받지 않는다.
