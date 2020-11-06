"use strict";

module.exports = {
  watchDirs: ["./assets"],
  js: [{
    source: "./assets/scripts/index.js",
    target: "./resources/public/bundle.js"
  }],
  sass: [{
    source: "./assets/styles/index.scss",
    target: "./resources/public/bundle.css"
  }]
};
