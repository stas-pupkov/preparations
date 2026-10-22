# Сборка: Maven и Gradle

Java 17+. Заметка для собеседований. В этом репозитории сборка на **Gradle**
(`build.gradle` + Wrapper); Maven разбираем рядом — на интервью спрашивают оба.

## Содержание

1. [Зачем build tool](#1-зачем-build-tool)
2. [Maven: основы](#2-maven-основы)
3. [Жизненный цикл Maven](#3-жизненный-цикл-maven)
4. [Зависимости в Maven](#4-зависимости-в-maven)
5. [Gradle: основы](#5-gradle-основы)
6. [Задачи и фазы Gradle](#6-задачи-и-фазы-gradle)
7. [Зависимости в Gradle](#7-зависимости-в-gradle)
8. [Dependency tree: зачем и как пользоваться](#8-dependency-tree-зачем-и-как-пользоваться)
9. [Maven vs Gradle](#9-maven-vs-gradle)
10. [Многомодульность](#10-многомодульность)
11. [BOM и выравнивание версий](#11-bom-и-выравнивание-версий)
12. [Генераторы и codegen](#12-генераторы-и-codegen)
13. [Практика и этот репозиторий](#13-практика-и-этот-репозиторий)
14. [Вопросы для собеседования и ответы](#14-вопросы-для-собеседования-и-ответы)

---

## 1. Зачем build tool

Build tool делает одно и то же в CI и на ноутбуке:

1. скачать зависимости;
2. скомпилировать код (и annotation processors);
3. прогнать тесты;
4. собрать артефакт (jar / war / bootJar);
5. при необходимости опубликовать в репозиторий.

Без него «у меня в IDE собралось» ≠ «собралось у всех». Координаты артефакта:

```text
groupId : artifactId : version
пример: org.springframework.boot : spring-boot-starter-web : 3.3.0
```

Репозитории: Maven Central, корпоративный Nexus/Artifactory, иногда локальный
`~/.m2/repository`.

---

## 2. Maven: основы

**Maven** — «конвенция важнее конфигурации». Описание проекта — `pom.xml`
(Project Object Model).

Типичная раскладка:

```text
src/main/java
src/main/resources
src/test/java
src/test/resources
target/                 ← результат сборки
pom.xml
```

Минимальный смысл `pom.xml`:

```xml
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>ru.stas.pupkov</groupId>
  <artifactId>demo</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <packaging>jar</packaging>

  <dependencies>
    <dependency>
      <groupId>com.h2database</groupId>
      <artifactId>h2</artifactId>
      <version>2.2.224</version>
      <scope>runtime</scope>
    </dependency>
  </dependencies>
</project>
```

Плагины (`maven-compiler-plugin`, `maven-surefire-plugin`, …) вешают **цели (goals)**
на фазы жизненного цикла. Сам Maven «знает» compile/test/package — ты чаще настраиваешь,
а не пишешь скрипт с нуля.

Wrapper: `mvnw` / `mvnw.cmd` — фиксированная версия Maven в репозитории (как `gradlew`).

---

## 3. Жизненный цикл Maven

У Maven **фиксированные фазы**. Вызвал фазу — выполняются все предыдущие по порядку.

Главный lifecycle (упрощённо):

```text
validate → compile → test → package → verify → install → deploy
```

| Команда | Что получишь |
|---------|----------------|
| `mvn compile` | классы в `target/classes` |
| `mvn test` | + unit-тесты |
| `mvn package` | + jar/war в `target/` |
| `mvn verify` | + проверки (интеграционные тесты, checkstyle…) |
| `mvn install` | артефакт в локальный `~/.m2` |
| `mvn deploy` | публикация в удалённый репозиторий |
| `mvn clean` | отдельный lifecycle: снести `target/` |

Важно: `package` уже прогоняет `test`. Чтобы собрать без тестов (осторожно в CI):

```bash
mvn package -DskipTests
# или жёстче: -Dmaven.test.skip=true (не компилирует тесты)
```

Плагин привязывается к фазе, например «сгенерировать источники до compile».

---

## 4. Зависимости в Maven

### Scopes

| Scope | Compile | Runtime | В транзитным consumer'ам |
|-------|---------|---------|---------------------------|
| `compile` (default) | да | да | да |
| `provided` | да | контейнер даст | нет |
| `runtime` | нет | да | да |
| `test` | только тесты | тесты | нет |
| `import` | только в `dependencyManagement` (BOM) | — | — |

Пример: JDBC-драйвер часто `runtime`; servlet API в старом WAR — `provided`.

### Транзитивность и конфликты

Зависимость A тянет B → B тоже попадает на classpath (если scope позволяет).

Конфликт версий одной библиотеки: Maven выбирает по правилу **nearest wins**
(ближайшая к корню в дереве; при равной глубине — порядок объявления). Это сюрпризы на
собесе: «почему подтянулась старая версия». Разбор графа — [§8](#8-dependency-tree-зачем-и-как-пользоваться)
(`mvn dependency:tree`).

`dependencyManagement` — задать версии «на будущее» без прямого подключения;
`<dependencies>` — реально взять артефакт.

Исключить транзитную зависимость:

```xml
<exclusions>
  <exclusion>
    <groupId>commons-logging</groupId>
    <artifactId>commons-logging</artifactId>
  </exclusion>
</exclusions>
```

---

## 5. Gradle: основы

**Gradle** — модель **графа задач (tasks)**, скрипт на Groovy DSL или Kotlin DSL
(`build.gradle` / `build.gradle.kts`).

Структура каталогов для Java — почти как у Maven (`src/main/java`, …). Выход — `build/`,
не `target/`.

Фрагмент в духе этого репозитория:

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.1.1'
    id 'io.spring.dependency-management' version '1.1.7'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
    runtimeOnly 'com.h2database:h2'
    annotationProcessor 'org.projectlombok:lombok'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

**Gradle Wrapper** (`gradlew`): в git кладут скрипт + `gradle/wrapper/`, команда всегда
одна и та же версия Gradle. На проде/CI — `./gradlew build`, а не «поставь Gradle руками».

Три фазы работы Gradle:

1. **Initialization** — какие проекты входят в сборку;
2. **Configuration** — строится граф задач (сейчас стараются ленивую конфигурацию);
3. **Execution** — выполняются нужные tasks.

---

## 6. Задачи и фазы Gradle

Нет жёсткой линейки фаз как у Maven. Есть tasks и зависимости между ними.

Частые lifecycle-tasks (Java Plugin / Base Plugin):

| Task | Смысл (близкий Maven) |
|------|------------------------|
| `clean` | удалить `build/` |
| `classes` | скомпилировать main |
| `test` | unit-тесты |
| `assemble` | собрать артефакты (jar…), **без** обязательных проверок |
| `check` | все проверки (тесты, static analysis) |
| `build` | `check` + `assemble` |
| `bootJar` / `bootRun` | Spring Boot (если подключён плагин) |

```bash
./gradlew tasks          # что есть
./gradlew build
./gradlew test
./gradlew assemble -x test
```

Свой task (как `runDemo` в этом проекте):

```groovy
tasks.register('runDemo', JavaExec) {
    classpath = sourceSets.main.runtimeClasspath
    mainClass = providers.gradleProperty('mainClass')
            .orElse('…PreparationsApplication')
}
```

Инкрементальность: Gradle старается не пересобирать то, что не изменилось (up-to-date,
build cache). На больших мультимодулях это заметный плюс против «всегда полный цикл».

---

## 7. Зависимости в Gradle

### Configurations (аналог scopes)

| Configuration | Смысл |
|---------------|--------|
| `implementation` | нужно для компиляции и runtime **этого** модуля; наружу API не светим |
| `api` | (java-library) часть публичного API модуля — утекает consumer'ам |
| `compileOnly` | только compile (аналог куска `provided`) |
| `runtimeOnly` | только runtime |
| `testImplementation` | тесты |
| `annotationProcessor` | Lombok, MapStruct, … |

`implementation` vs `api` — частый senior-вопрос: не тащить внутренние библиотеки в
classpath потребителей модуля.

### Конфликты версий

Gradle по умолчанию берёт **наибольшую** версию среди кандидатов в графе (не nearest-wins
как Maven). Можно ужесточить:

```groovy
dependencies {
    implementation('com.google.guava:guava') {
        version { strictly '32.1.3-jre' }
    }
}
```

Разбор графа: `./gradlew dependencies` и `dependencyInsight` — [§8](#8-dependency-tree-зачем-и-как-пользоваться).

---

## 8. Dependency tree: зачем и как пользоваться

**Dependency tree** — полное дерево библиотек: то, что объявил ты, и то, что пришло
**транзитивно**. В `pom` / `build.gradle` видны только прямые строки; ломает classpath
часто «чужой» jar из глубины графа.

### Зачем смотреть

1. Узнать, **кто притащил** библиотеку (Guava через Spring и ещё через Google client).
2. Понять, **какая версия победила** при конфликте.
3. Найти источник CVE / устаревшего пакета.
4. Проверить, сработал ли exclusion / BOM: старая ветка реально исчезла.

Типичные триггеры: `NoSuchMethodError`, «странный» `ClassNotFoundException`, раздутый
fat-jar, security-scan указал на транзитив.

### Maven

```bash
mvn dependency:tree
mvn dependency:tree -Dincludes=com.google.guava
mvn dependency:tree -Dverbose
```

Как читать: отступы = «подтянул родитель». При конфликте часто пометка вроде
`omitted for conflict with x.y.z` — эта версия отброшена в пользу другой.

### Gradle

Всё дерево (шумно на большом проекте):

```bash
./gradlew dependencies
```

Узже — только нужная configuration:

```bash
./gradlew dependencies --configuration runtimeClasspath
./gradlew dependencies --configuration compileClasspath
```

Почему выбрана конкретная зависимость / версия:

```bash
./gradlew dependencyInsight --dependency guava --configuration runtimeClasspath
./gradlew dependencyInsight --dependency jackson-databind --configuration runtimeClasspath
```

| Команда | Роль |
|---------|------|
| `dependencies` | обзор графа целиком (или по configuration) |
| `dependencyInsight` | «откуда и почему именно эта версия» |

В этом репозитории:

```bash
./gradlew dependencies --configuration runtimeClasspath
```

### Практический сценарий

1. В рантайме jackson 2.12, а ждали 2.15.
2. `dependencyInsight --dependency jackson-databind` → видно модуль-источник старой версии.
3. Чинишь: BOM / явная версия / `strictly` / `exclusion` у виновника.
4. Снова дерево — подтверждаешь, что осталась одна нужная версия.

Если после exclusion библиотека всё ещё в дереве — её тянет **другая** ветка; exclusion
нужен у каждого «родителя» или лучше выровнять версию сверху (BOM / force / strictly).

---

## 9. Maven vs Gradle

| | Maven | Gradle |
|--|-------|--------|
| Модель | фиксированные фазы + plugins/goals | граф tasks |
| Конфиг | XML `pom.xml` | Groovy/Kotlin DSL |
| Гибкость | проще «стандартный» путь | проще кастомные пайплайны |
| Скорость | часто полный проход по фазам | инкрементальность, configuration cache, build cache |
| Конфликт версий | nearest wins | обычно highest version |
| Сложность входа | ниже | выше (DSL, configurations) |

Когда что:

- **Maven** — единообразие в большой org, много готовых плагинов, команда «не хочет DSL»;
- **Gradle** — Android/Spring Boot экосистема, сложная мультимодульность, кастомные tasks,
  ускорение CI.

Оба качают артефакты из Maven-репозиториев. «Gradle вместо Maven Central» — нет:
Central — склад библиотек, Gradle/Maven — клиенты склада.

---

## 10. Многомодульность

### Maven

```text
parent/pom.xml          ← packaging pom, modules
  module-api/pom.xml
  module-service/pom.xml
```

Parent задаёт `dependencyManagement`, плагины, Java version. Модули наследуют и
объявляют друг друга:

```xml
<dependency>
  <groupId>…</groupId>
  <artifactId>module-api</artifactId>
  <version>${project.version}</version>
</dependency>
```

### Gradle

```text
settings.gradle   ← include 'api', 'service'
api/build.gradle
service/build.gradle
```

```groovy
// service/build.gradle
dependencies {
    implementation project(':api')
}
```

Межмодульная зависимость в Gradle не требует `install` в `.m2` — задача другого
проекта участвует в графе. В Maven для «подсмотреть локально» часто нужен `mvn install`
модуля (или reactor-сборка из корня `mvn -pl … -am`).

---

## 11. BOM и выравнивание версий

**BOM (Bill of Materials)** — pom только с `dependencyManagement`: набор согласованных
версий (Spring Boot, Jackson, …).

Maven:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-dependencies</artifactId>
      <version>3.3.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Дальше зависимость без `<version>` — версия из BOM.

Gradle + Spring:

- плагин `io.spring.dependency-management` импортирует Boot BOM (как в этом репо);
- или платформа: `implementation platform('org.springframework.boot:spring-boot-dependencies:…')`.

Смысл: не ловить «Boot 3.3 + случайный старый Spring Data».

---

## 12. Генераторы и codegen

Три разные вещи (их часто смешивают на собесе):

| Вид | Когда | Примеры |
|-----|-------|---------|
| Annotation processor | во время `javac` | Lombok, MapStruct, Immutables |
| Codegen plugin/task | до/рядом с compile | protobuf, OpenAPI Generator, JAXB |
| Archetype / шаблон | один раз при создании проекта | `maven-archetype-quickstart` |

### Annotation processors

Пишешь аннотацию → процессор генерирует исходники.

Maven — `annotationProcessorPaths` у `maven-compiler-plugin`.  
Gradle — конфигурация `annotationProcessor`:

```groovy
annotationProcessor 'org.projectlombok:lombok'
annotationProcessor 'org.mapstruct:mapstruct-processor:1.5.5.Final'
```

Куда кладут: `target/generated-sources/…` (Maven) / `build/generated/…` (Gradle).

### Плагины codegen

```text
.proto / openapi.yaml / xsd
    → generate task
    → generated sources
    → compile вместе с ручным кодом
```

Maven: `protobuf-maven-plugin`, `openapi-generator-maven-plugin`, `cxf-codegen-plugin`.  
Gradle: те же генераторы как plugins/tasks (`openApiGenerate`, protobuf plugin).

### Archetype

```bash
mvn archetype:generate -DgroupId=com.acme -DartifactId=demo
```

Это скелет проекта один раз, не часть каждого CI-билда.

Практика:

- generated code обычно **не коммитят**;
- в VCS — контракт (`.proto`, OpenAPI);
- версии генератора и схемы фиксируют, иначе «у меня другие классы».

---

## 13. Практика и этот репозиторий

| Тема | Здесь |
|------|--------|
| Сборка | Gradle Wrapper + `build.gradle` |
| Java | toolchain 17 |
| Boot | `spring-boot` + `dependency-management` |
| Кастомный task | `runDemo` с `-PmainClass=…` |
| Lombok | `annotationProcessor` |

Полезные команды:

```bash
./gradlew build
./gradlew test
./gradlew runDemo -PmainClass=ru.stas.pupkov.preparations.kafka.code.DeliverySemanticsDemo
```

Типичные грабли на проде:

1. Нет Wrapper в git → «у меня другая версия Gradle».
2. Зависимости без BOM → ад совместимости.
3. `./gradlew build` падает только в CI: разный JDK (лечится toolchain).
4. Путают `assemble` (пакет без check) и `build` (с проверками).
5. В Maven `install` «чтобы соседний модуль увидел» вместо сборки из корневого reactor.

---

## 14. Вопросы для собеседования и ответы

### 1. Чем Maven отличается от Gradle?

Maven — фиксированный lifecycle фаз и XML-конвенции. Gradle — граф tasks и DSL, обычно
лучше инкрементальность и кастомизация. Оба тянут артефакты из Maven-репозиториев.
См. [§9](#9-maven-vs-gradle).

### 2. Что делает `mvn package` и что входит «по дороге»?

Фазы до `package` включительно: в том числе `compile` и `test`. На выходе — jar/war в
`target/`. См. [§3](#3-жизненный-цикл-maven).

### 3. Чем `implementation` отличается от `api` в Gradle?

`implementation` — зависимость модуля, наружу не экспортируется. `api` — часть
публичного API: попадёт на compile classpath потребителей. Для library-модулей это
граница инкапсуляции.

### 4. Как Maven и Gradle разруливают конфликт версий одной библиотеки?

Maven — nearest wins (глубина в дереве). Gradle — обычно highest version, плюс правила
`strictly` / resolution strategies. Всегда смотри дерево зависимостей —
[§8](#8-dependency-tree-зачем-и-как-пользоваться).

### 5. Зачем Gradle Wrapper?

Одинаковая версия Gradle у всех и в CI без ручной установки. В репозиторий кладут
`gradlew` + `gradle/wrapper/`.

### 6. Что такое BOM?

Pom (или platform) с согласованным набором версий. Импортируешь — зависимости можно
объявлять без явной version. Spring Boot dependencies — классический BOM.
См. [§11](#11-bom-и-выравнивание-версий).

### 7. Чем MapStruct отличается от OpenAPI Generator?

MapStruct — annotation processor на этапе компиляции Java. OpenAPI Generator — отдельный
codegen из спецификации API (plugin/task), часто целые классы клиента/сервера.
См. [§12](#12-генераторы-и-codegen).

### 8. Куда девать сгенерированный код?

В `target/generated-sources` (Maven) / `build/generated` (Gradle). В VCS — редко;
контракт-источник (`.proto`, yaml) — да.

### 9. Чем `./gradlew assemble` отличается от `./gradlew build`?

`assemble` — собрать артефакты. `build` — ещё и `check` (тесты и прочие проверки).
Для «просто jar» иногда хватает `assemble`; для CI обычно `build`.

### 10. Зачем `annotationProcessor`, а не `implementation` для Lombok?

Процессор нужен компилятору на этапе генерации кода, а не (обязательно) в runtime
classpath приложения. Путать конфигурации — лишний classpath или «Lombok не сработал».

### 11. Зачем dependency tree и чем `dependencies` отличается от `dependencyInsight`?

Дерево показывает прямые и транзитивные зависимости — кто что притащил и какая версия
на classpath. В Gradle `dependencies` — обзор графа; `dependencyInsight` — почему
выбрана конкретная библиотека/версия. В Maven — `mvn dependency:tree`.
См. [§8](#8-dependency-tree-зачем-и-как-пользоваться).

### 12. Почему после exclusion библиотека всё ещё в дереве?

Exclusion режет связь только у указанного родителя. Ту же зависимость может тянуть
другая ветка — смотри дерево и либо исключи ещё раз, либо выровняй версию сверху
(BOM / force / `strictly`).
