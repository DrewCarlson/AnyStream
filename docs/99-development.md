# Development

## Writing Docs

Documentation is created with [MkDocs](https://www.mkdocs.org/)
using [Material for MkDocs](https://squidfunk.github.io/mkdocs-material/). MkDocs is configured with `mkdocs.yml` and
documentation source is stored in the `docs` folder.

### Install Python

[Download](https://www.python.org/downloads/) and install the latest version of Python.

#### macOS

Using [Homebrew](https://brew.sh/)

```bash
brew install python
```

#### Windows

[Download](https://www.python.org/downloads/) and install the recommended version
from [python.org](https://www.python.org/).

Or with [Chocolatey](https://chocolatey.org/)

```shell
choco install python
```

### Install MkDocs and Extensions

```shell
pip install mkdocs mkdocs-material
```

For more information see the MkDocs [Installation Guide](https://www.mkdocs.org/getting-started/#installation) and the
Material for MkDocs [Installation Guide](https://squidfunk.github.io/mkdocs-material/getting-started/#with-pip).

### View docs locally

To view the docs locally, open a terminal or command prompt and cd into your `anystream` folder then run

```shell
mkdocs serve
```

Your changes will be served at [http://127.0.0.1:8000](http://127.0.0.1:8000). After saving changes, the webpage will
reload automatically.

### Deployment

Changes are deployed to [docs.anystream.dev](https://docs.anystream.dev/) automatically when merged into the `main`
branch.