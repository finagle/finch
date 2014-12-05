Finch Jawn
----------

This module provides support for using [Jawn](https://github.com/non/jawn) in Finch.

## Decoding Json

To decode a string with Jawn, you need to import `io.finch.json.jawn._` and define any Jawn `Facade` as an implicit val.
Using either `RequiredJsonBody` or `OptionalJsonBody` will take care of the rest.


## Encoding Json

To Encode a value from the Jawn ast (specifically a `JValue`), you need only import `io.finch.json.jawn._` and
the Encoder will be imported implicitly.

