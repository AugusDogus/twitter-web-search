<h1 align="center">Twitter Web Search</h1>

<p align="center">Select text. Search the web.</p>

<p align="center">
  <a href="https://github.com/AugusDogus/twitter-web-search/actions/workflows/build.yml">Download</a> ·
  <a href="docs/development.md">Development</a> ·
  <a href="VALIDATION.md">Tested versions</a>
</p>

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="design/selection.dark.svg">
  <img src="design/selection.light.svg" width="100%" alt="Twitter's text-selection menu before and after patching: Ask Grok becomes Web Search. Copy, Mute, and Select all stay in place.">
</picture>

A [Morphe](https://github.com/MorpheApp/morphe-manager) patch that replaces **Ask Grok** with Android’s **Web Search** action. Search the selected text with your preferred app. Copy, Mute, and Select all stay in place.

## Install

1. Download **twitter-web-search** from a successful [build](https://github.com/AugusDogus/twitter-web-search/actions/workflows/build.yml) and unzip it. GitHub sign-in is required.
2. In Morphe, open **Sources → Add → Local** and select the `.mpp`.
3. Select Twitter, enable **NewX: Web search selected text**, then patch and install. Use Expert mode to combine it with Piko.

For **Piko 3.59.0**, use **Twitter 12.35.0-prod.01**. This combination was verified on Android 17 with Morphe 1.35.0, including opening search results for the selected text.

The standalone patch also supports 12.25.2 and 12.36.1. That does not imply support from Piko’s other patches. See [validation](VALIDATION.md) for details.

Independent companion to [Piko](https://github.com/crimera/piko-newx). [GPL-3.0-or-later](LICENSE).
