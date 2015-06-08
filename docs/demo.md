## Demo

There is a simple `demo` project, which is a complete REST API backend written with `finch-core` and `finch-argonaut`
modules. The source code of the demo project is altered with useful comments that explain how to use its building blocks 
such as `Router`, `RequestReader` and `ResponseBuilder`. 

The following command may be used to run the demo:

```bash
sbt 'project demo' 'run io.finch.demo.Main'
```
--
Read Next: [Routes](route.md)
