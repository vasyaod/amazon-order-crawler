# Amazon order crawler

Simple CLI for crawling and parsing of collecting list of made orders based on ZIO lib and Microsoft Playwright.

```
mvn compile exec:java -Dexec.mainClass=com.fproj.aoc.Main -Dexec.args="--headless -l $USERNAME -p $PASSWORD"
```

## Parameters

```
Usage: amazon-order-crawler [options]

  -l, --login <value>      login/username for amazon site
  -p, --password <value>   password for amazon site
  -h, --headless           headless mode
  --url <value>            Amazon url, default is http://amazon.com/. It could depend on a country
  --years <year1>,<year2>...
                           Kind of filter for what years orders should be downloaded
```