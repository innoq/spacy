# Compile frontend assets

FROM node AS faucet
WORKDIR /app
ADD package.json ./
RUN npm install
ADD faucet.config.js .
ADD assets assets/
RUN npm run compile

# Get all backend dependencies

FROM adoptopenjdk:11-jre-hotspot AS lein

RUN useradd -m spacy
USER spacy
WORKDIR /home/spacy

RUN curl \
  https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein \
  -o lein \
  && chmod +x lein
RUN ./lein version

COPY project.clj .
RUN ./lein deps
RUN ./lein cp ./classpath

# Build the target image

FROM adoptopenjdk:11-jre-hotspot

RUN useradd -m spacy
USER spacy
WORKDIR /home/spacy

COPY --from=lein /home/spacy/.m2 /home/spacy/.m2/
COPY --from=lein /home/spacy/classpath ./classpath
COPY --from=faucet /app/resources/public resources/public/
COPY resources resources/
COPY src src/

CMD java -cp $(cat classpath) clojure.main -m spacy.main
