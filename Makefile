.PHONY: build test run burst docker-up

build:
	./gradlew bootJar

test:
	./gradlew test

run:
	./gradlew bootRun

docker-up:
	docker compose up --build

burst:
	bash burst.sh $(BASE_URL) $(CONCURRENCY) $(HOT_USERS)
