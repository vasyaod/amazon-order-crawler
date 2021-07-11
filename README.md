# Amazon order crawler

Simple for crawler and parser for collecting list of made orders

```
mvn compile exec:java -Dexec.mainClass=com.fproj.aoc.Main -Dexec.args="--headless -l $USERNAME -p $PASSWORD"
```

## Parameters

```
Usage: amazon-order-crawler [options]

  -l, --login <value>     login/username for amazon site
  -p, --password <value>  password for amazon site
  -h, --headless          headless mode
```