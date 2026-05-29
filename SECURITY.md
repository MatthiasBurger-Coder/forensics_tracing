# Security Policy

## Reporting Security Issues

This repository contains Gradle and Maven plugins that submit build context to a Forensics Analytics server over gRPC. Security reports may involve build-tool configuration handling, gRPC transport, dependency handling, metadata exposure, or unsafe handling of server responses.

Please do not open a public issue or pull request for a suspected vulnerability before it has been assessed privately. Public disclosure can make it easier for others to exploit the issue before a fix is available.

Use GitHub private vulnerability reporting for this repository when it is available. If that option is not available, contact the repository maintainers privately through the contact channels published on the repository owner profile or project page.

## What To Include

When reporting a vulnerability, include:

- a clear description of the issue and affected component
- steps to reproduce the issue with the smallest practical example
- the affected version, commit, or build-tool plugin configuration
- relevant gRPC endpoint, transport, or metadata details
- whether the issue exposes sensitive data, weakens build isolation, or allows unsafe server communication

Do not include secrets, credentials, private customer data, or full production build metadata unless the maintainers explicitly request a sanitized sample.

## Supported Scope

Reports are most useful when they target the current repository state or the latest published plugin version. Older versions may be assessed case by case, depending on severity and available maintenance capacity.

## Disclosure

Please give the maintainers a reasonable opportunity to investigate and prepare a fix before public disclosure. Coordinated disclosure helps users update safely and keeps the public record accurate.
