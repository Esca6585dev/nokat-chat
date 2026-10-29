FROM golang:1.24-alpine AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -ldflags="-s -w" -o /out/nokat-server ./cmd/server

FROM alpine:3.20
RUN adduser -D -H nokat && mkdir /data && chown nokat /data
USER nokat
COPY --from=build /out/nokat-server /usr/local/bin/nokat-server
ENV ADDR=:8080 DB_DRIVER=sqlite DB_DSN=/data/nokat.db
EXPOSE 8080
CMD ["nokat-server"]
