#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="${CODECRITIC_JAR:-$ROOT/verification/target/codecritic-verification.jar}"

if [ ! -f "$JAR" ]; then
  echo "Missing verification jar: $JAR" >&2
  echo "Run: mvn -B -pl verification -am package -DskipTests" >&2
  exit 2
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
REPO="$WORK/repo"
REPORT="${CODECRITIC_E2E_REPORT:-$WORK/report.json}"

mkdir -p \
  "$REPO/lib/src/main/java/com/example/lib" \
  "$REPO/lib/src/test/java/com/example/lib" \
  "$REPO/app/src/main/java/com/example/app" \
  "$REPO/app/src/test/java/com/example/app"

cat > "$REPO/pom.xml" <<'POM'
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.example</groupId>
  <artifactId>e2e-parent</artifactId>
  <version>1.0</version>
  <packaging>pom</packaging>
  <modules>
    <module>lib</module>
    <module>app</module>
  </modules>
  <properties>
    <maven.compiler.release>21</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>
  <build>
    <pluginManagement>
      <plugins>
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-compiler-plugin</artifactId>
          <version>3.13.0</version>
        </plugin>
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-surefire-plugin</artifactId>
          <version>3.2.5</version>
        </plugin>
      </plugins>
    </pluginManagement>
  </build>
</project>
POM

cat > "$REPO/lib/pom.xml" <<'POM'
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.example</groupId>
    <artifactId>e2e-parent</artifactId>
    <version>1.0</version>
    <relativePath>../pom.xml</relativePath>
  </parent>
  <artifactId>lib</artifactId>
  <dependencies>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>5.10.2</version>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
POM

cat > "$REPO/app/pom.xml" <<'POM'
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.example</groupId>
    <artifactId>e2e-parent</artifactId>
    <version>1.0</version>
    <relativePath>../pom.xml</relativePath>
  </parent>
  <artifactId>app</artifactId>
  <dependencies>
    <dependency>
      <groupId>com.example</groupId>
      <artifactId>lib</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>5.10.2</version>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
POM

cat > "$REPO/lib/src/main/java/com/example/lib/Calculator.java" <<'JAVA'
package com.example.lib;

public final class Calculator {
    public static int add(int a, int b) {
        return a + b;
    }
}
JAVA

cat > "$REPO/lib/src/test/java/com/example/lib/CalculatorTest.java" <<'JAVA'
package com.example.lib;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalculatorTest {
    @Test
    void addsPositiveNumbers() {
        assertEquals(3, Calculator.add(1, 2));
    }
}
JAVA

cat > "$REPO/app/src/main/java/com/example/app/Service.java" <<'JAVA'
package com.example.app;

import com.example.lib.Calculator;

public final class Service {
    public static int sum(int a, int b) {
        return Calculator.add(a, b);
    }
}
JAVA

cat > "$REPO/app/src/test/java/com/example/app/AppTest.java" <<'JAVA'
package com.example.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppTest {
    @Test
    void addsNegativeNumbers() {
        assertEquals(1, Service.sum(-1, 2));
    }
}
JAVA

git -C "$REPO" init -q
git -C "$REPO" config user.email "codecritic@example.invalid"
git -C "$REPO" config user.name "CodeCritic E2E"
git -C "$REPO" add .
git -C "$REPO" commit -q -m "base"

cat > "$REPO/lib/src/main/java/com/example/lib/Calculator.java" <<'JAVA'
package com.example.lib;

public final class Calculator {
    public static int add(int a, int b) {
        return a < 0 ? a - b : a + b;
    }
}
JAVA

git -C "$REPO" add .
git -C "$REPO" commit -q -m "regress lib for negative inputs"

set +e
CODECRITIC_SANDBOX_NETWORK="${CODECRITIC_SANDBOX_NETWORK:-bridge}" \
  java -jar "$JAR" \
    --repo "$REPO" \
    --base HEAD~1 \
    --head HEAD \
    --run-tests \
    --select modules \
    --fail-on new-failure \
    --format json \
    --test-timeout 600 \
    --out "$REPORT"
STATUS=$?
set -e

if [ "$STATUS" -ne 1 ]; then
  echo "Expected gate exit 1, got $STATUS" >&2
  cat "$REPORT" >&2 || true
  exit 1
fi

grep -q '"app"' "$REPORT"
grep -q '"lib"' "$REPORT"
grep -q '"TEST_SELECTION"' "$REPORT"
grep -q '"NEW_FAILURE"' "$REPORT"
grep -q '"com.example.app.AppTest"' "$REPORT"

echo "E2E module gate passed: changed lib selected app and failed on the new dependent test."
