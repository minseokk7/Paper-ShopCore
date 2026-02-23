<div align="center">

# ShopCore - Backend Plugin

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.10-green?style=for-the-badge&logo=minecraft)
![Velocity](https://img.shields.io/badge/Platform-Velocity-0066CC?style=for-the-badge&logo=velocity&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)

[한국어](README.md) | [English](README_EN.md)

</div>


이 플러그인은 **ShopCore**의 백엔드 부분으로, Spigot/Paper/Purpur 서버에서 실행됩니다.

## 주요 기능
- **상점 GUI**: 플레이어가 아이템을 구매하고 판매할 수 있는 GUI 제공.
- **동적 가격 시스템**: 거래량에 따라 아이템 가격이 변동되는 경제 시스템.
- **데이터베이스 연동**: MySQL을 사용하여 가격 및 거래 내역 저장.
- **서버 간 동기화**: Velocity 프록시를 통해 다른 서버와 가격 정보를 실시간으로 동기화.

## 설치 방법
1. `ShopCore-1.0.2.jar` 파일을 각 서버의 `plugins` 폴더에 넣습니다.
2. 서버를 시작하여 `config.yml` 파일을 생성합니다.
3. `config.yml`에서 데이터베이스 연결 정보를 설정합니다.

## 설정 (config.yml)
```yaml
server-name: "lobby" # 각 서버마다 고유한 이름 설정 (lobby, survival, creative 등)
database:
  host: "localhost"
  port: 3306
  database: "minecraft_db"
  username: "user"
  password: "password"
```

## 명령어
- `/shop`: 상점 GUI 열기
- `/shop setprice <아이템> <구매가> <판매가>`: 아이템 가격 설정 (관리자)
- `/shop reset <아이템>`: 아이템 가격 초기화 (관리자)
