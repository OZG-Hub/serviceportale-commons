# The Serviceportal Commons Collection

The Serviceportal is an e-government platform used in the German federal states Baden-Württemberg (branded [Service-BW](https://www.service-bw.de/)), Sachsen (branded [Amt 24](https://amt24.sachsen.de/)) and nationwide (branded [OZG-Hub](https://www.ozg-hub.de/)).
This repository contains various classes, methods and tools useful for developing processes on the Serviceportal.

Examples:
- Validators for common use cases in web forms that are not natively included in the Serviceportal (e.g. IBAN, minimum age)
- Methods to dump the content form classes used in the Serviceportal API to text, CSV or HTML.
- A class to quickly create a suitable data source for a Select-Option field listing all countries.

# License
Copyright 2023 SEITENBAU GmbH

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

# Usage
You're free to use this repository in the way you find the most useful. You could copy & paste code snippets into your own models if you want. However, we found the best way to ensure your process model stays up-to-date with the latest developments in the Commons collection is by including it into your build pipeline as a [git submodule](https://git-scm.com/book/en/v2/Git-Tools-Submodules).

To do so, configure, initialize and update the new git submodule (in the `commons/serviceportal` subfolder):

```bash
git submodule add https://github.com/OZG-Hub/serviceportale-commons.git commons/serviceportal
git submodule init
git submodule update
```

Then ensure the project is included in your build pipeline. If you use gradle, you'd edit `settings.gradle` to add:

```
rootProject.name = '<YOUR_MAIN_PROJECT_NAME_HERE>'
include "commons.serviceportal"
project(':commons.serviceportal').projectDir = new File('commons/serviceportal')
```

And then add the project dependency in `build.gradle`:

```groovy
dependencies {
  // all your other dependencies here
  
  // Serviceportal commons collection
  compile project("commons.serviceportal")
}
```

# Contributing
This project was initially developed by SEITENBAU but contributions from other parties are welcome and help to improve the Serviceportale ecosystem. If you fixed a bug, added a new feature or found another way to improve this project, feel free to [open a Pull Request](https://github.com/OZG-Hub/serviceportale-commons/pulls) if you already have some code ready. If you are not so far and want to start a discussion before (e.g. if you have a request for a feature or want to collaborate with someone else), feel free to [open an issue](https://github.com/OZG-Hub/serviceportale-commons/issues) (in German or English).
