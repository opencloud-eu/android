## Submitting issues

If you have questions about how to use OpenCloud, please join our matrix channel.

### Guidelines
* [Report the issue](https://github.com/opencloud-eu/android/issues/new), they include all the information we need to track down the issue.
* This repository is *only* for issues within the Android app code. Issues in other components should be reported in their own repositories: 
  - [Repositories](https://github.com/opencloud-eu)
* Search the [existing issues](https://github.com/opencloud-eu/android/issues) first, it's likely that your issue was already reported.

If your issue appears to be a bug, and hasn't been reported, open a new issue.

Help us to maximize the effort we can spend fixing issues and adding new features, by not reporting duplicate issues.



## Contributing to Source Code

Thanks for wanting to contribute source code to OpenCloud. That's great!

Before we're able to merge your code into the OpenCloud app for Android, please, check the contribution guidelines.

### Guidelines
* Contribute your code in a feature, fix, improvement or technical enhancement  branch by using  one of the following branch names:

     - ```feature/feature_name``` → new features in the app
     - ```fix/fix_name``` → fixing problems or bugs, always welcome!
     - ```improvement/improvement_name``` → make even better an existing feature
     - ```technical/technical_description```  → code review, DB... technical stuff improved

	Please, use the mentioned prefixes because CI system is ready to match with them. Be sure your feature, fix, improvement or technical branches are updated with latest changes in official `android/main`, it will give us a better chance to test your code before merging it with stable code.
* Once you are done with your code, start a pull request to merge your contribution into official `android/main`.
* Keep on using pull requests for your next contributions although you own write permissions.
* Important to mention that ÒpenCloud Android team uses OneFlow as branching model. It's something as useful as easy:

  * `main` will stay as main branch. Everything will work around it.
  * Feature branch: new branch created from `main`. Once it is finished and DoD accomplished, rebased and merged into `main`.
  * Release branch: will work as any feature branch. Before rebasing and merging into `main`, release tag must be signed.
  * Hotfix branch: created from latest tag. Once it is finished, tag must be signed. Then, rebased and merged into `main`.
  * The way to get an specific version is browsing through the tags.

	Interesting [link](https://www.endoflineblog.com/oneflow-a-git-branching-model-and-workflow) about this.

### 1. Fork and download android/main repository:

* Please follow [SETUP.md](https://github.com/opencloud-eu/android/blob/main/SETUP.md) to setup OpenCloud Android app work environment.

### 2. Create pull request:

* Create new feature, fix, improvement or technical enhancement branch from your main branch: ```git checkout -b feature/feature_name```
* Register your changes: `git add filename`
* Commit your changes locally. Please, if posible use [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) to add descriptive messages to the commits. Take the simplest approach:
	- Feature commits: `feat: brief description of the changes performed`
	- Fix commits: `fix: brief description of applied fix`
	- Test commits: `test: brief description of developed tests`
	- Calens commits: `chore: add calens file`

	Submit the commit with ```git commit -m "commit message"```
* Push your changes to your GitHub repo: ```git push origin feature/feature_name```
* Browse to https://github.com/YOURGITHUBNAME/android/pulls and issue pull request
* Enter description and send pull request.

### 3. Update your contribution branch with main changes:

It is possible you see the next message from time to time.

<img src="docs_resources/out_of_date_branch.png" />

To fix this and make sure your contribution branch is updated with official android/main, you need to perform the next steps:
* Checkout your main branch: ```git checkout main```
* Get and apply official android/main branch changes in your main branch: ```git fetch upstream``` + ```git rebase upstream/main```. Now you have your main branch updated with official main branch changes.
* Checkout your contribution branch: ```git checkout feature/feature_name```
* Rebase contribution branch with main to put your contribution commits after the last commit of main branch, ensuring a clean commits history: ```git rebase main```. If there's some conflicts, solve it by using rebase in different steps.
* Push branch to server: ```git push -f origin feature/feature_name```. At this point, the message ```This branch is out-of-date with the base branch``` should disappear.

## Versioning

In order to check or review the stable versions, all available tags can be fetched with the command `git fetch --tags` and listed with the command `git tag`. The tag `latest` is also available pointing to the latest released version.

## Translations
Please submit translations via [Transifex][transifex].

[transifex]: https://explore.transifex.com/opencloud-eu/opencloud-eu/

## Code of conduct
Please, read the [code of conduct]. Being respectful and polite with other members of the community and staff is necessary to develop a better product together.

[code of conduct]: https://opencloud.eu
