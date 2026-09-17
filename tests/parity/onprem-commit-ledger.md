# On-prem-only commit ledger (Phase 3)

Every `onprem-master` commit with no patch-equivalent on `cloud-master` (`git cherry origin/cloud-master origin/onprem-master`, 2026-09-17).
Status is one of: `present` (cloud already has the behaviour) · `ported <sha>` · `obsolete <reason>` · `todo`.
Version bumps and whitespace commits are pre-classified `obsolete`.

| Date | SHA | Subject | Files | Status |
|---|---|---|---|---|
| 2021-06-02 | `1f8da141d790aed62dbb7d98e8e31bb16455cede` | updates to how the ConfigurationService configurations bind to fix a bug with multiple accounts | 2 | todo |
| 2021-06-02 | `91c6323e4e2018227131f1b244375c91618291dc` | whitespace updates to POM files | 2 | obsolete (version/housekeeping) |
| 2021-07-13 | `7d9abf33da165e5c4f0c24f766a876fe672676f1` | fixed AEM_NO_DAM tag default issue | 1 | todo |
| 2021-07-13 | `f47186d76ce561206b5bed1688b71cde58e1af78` | updated other instance of AEM_NO_DAM logic issue | 1 | todo |
| 2021-07-26 | `46045d3462e7c1785acb87fa3bcea8058c91fcba` | updated dialog dropdown logic to support proxy components | 5 | todo |
| 2021-07-26 | `132871d2b781f0b0f58d73633a5d7367af4c4b76` | removed SNAPSHOT from packages | 3 | obsolete (version/housekeeping) |
| 2021-08-24 | `d9b3a820c6678e2a1bffa4299e9dd677b6ef6486` | Updates to ensure latest connector is recognizable | 3 | todo |
| 2021-08-26 | `9d8f23a82211e6bc476d284b14ef7d3a910fd219` | updated to fix the dropdown issue with multiple accounts BC-33 | 2 | todo |
| 2021-10-05 | `645887ee5db6af14a044f21a3f378658d722b422` | Added field length limitations to Brightcove metadata tab; Swapped Coral Select for Coral Autocomplete in Brightcove Video Player component | 3 | todo |
| 2021-10-07 | `a2d76c6220c5cffdf4ad50de4115a7684f1e4223` | AutoDialog update for non-BC assets | 2 | todo |
| 2021-10-07 | `68bd1c0ea5411e237d6efc49b6f20cadc12271c9` | version bump to release | 3 | obsolete (version/housekeeping) |
| 2021-10-14 | `befcebf8d68be328165f48e03a2dddef81f1750e` | BC-55: fix to dynamic player dropdown for multiple player accounts | 1 | todo |
| 2021-10-26 | `8d2534930bd3a4cffb367f3944142e6b157296d0` | In-Page Experience Fixes | 3 | todo |
| 2021-10-31 | `fd4f9c92bdc249b0b3878ab2b3ba4993c3c38cd3` | BC-57 issue with playlist component | 2 | todo |
| 2022-04-07 | `5f9629d1ddb1c8e965b9768d13cdf2fbd5d6f63b` | BC-58 - removed legacy package path | 4 | todo |
| 2022-04-07 | `ad56a3213f2552bcb49b70527b458580e3bdd34d` | Updated Brightcove Loading Animation and Icon | 2 | todo |
| 2022-04-07 | `85e847f41fd7adbf0a432be710a344cbbceb50ac` | BC-57 - Updated Video Dropdown to Include Name and ID in Values | 3 | todo |
| 2022-04-07 | `c501c9565dba77dc93395f7079813877645d4d53` | version update | 3 | obsolete (version/housekeeping) |
| 2022-04-11 | `4f4b6672b238f8c23e620c0a5405ae0f3e3f9864` | BC-59 :: Fixed an issue with aggressive encoding of query parameters like the colon | 1 | todo |
| 2022-06-22 | `447df943c4fde57621873b43441a240c518e5480` | labels dropdown and API methods; initial clips UI support | 7 | todo |
| 2022-06-22 | `144153119f107caffac090bbe0556418f8084319` | fixed video count in console when searching by folder or label | 2 | todo |
| 2022-06-23 | `bf634185590a50ec7036c82258a4b652c3728916` | Updated BC logo on header | 4 | todo |
| 2022-07-28 | `1ee4eb9ea1c3f6e419aef22399145b28a9334c36` | Updated logging and testing library dependencies | 2 | todo |
| 2022-07-28 | `b3ed2570dea4b9717cf5cea1494bc7cadd4be82d` | updated release version to 6.0.0 | 3 | obsolete (version/housekeeping) |
| 2022-07-28 | `eb27f28a0f54609d0f6f690ff9116cfcd36d9c29` | Removed dependency on older Bootstrap; updated styles | 9 | todo |
| 2022-07-28 | `c97283308c0cdb5f18c9680bc2c1cf0192b8f0ad` | updated jQuery to 3.5.1 | 1 | todo |
| 2022-07-31 | `63a2ce62b7180272bdcf2f35e8b033ec778bb7f5` | updated license files | 2 | obsolete (version/housekeeping) |
| 2022-08-04 | `8090292b76e7e202766c1430124df7ab511c1432` | fixed metadata preview and playlist header styles | 3 | todo |
| 2022-08-04 | `80ca1a53df596af83a6c5ab09fe5b9ce52cfce15` | updated playlist creation logic and styles | 9 | todo |
| 2022-08-05 | `dd6b81380d5a48bfc9b130ef4912d4e4826b700c` | labels schema support; junit dependency version bump | 5 | obsolete (version/housekeeping) |
| 2022-08-11 | `a3010dc7cc78371a94b2161b2888d2c0c9d5f5b4` | initial UI for labels | 6 | todo |
| 2022-08-11 | `b3b92cf29b63a4bb646796fb3d1870a765a278ae` | better label support; creating labels support | 6 | todo |
| 2022-08-12 | `a09d24278bd96a68d7dc66cb266f7617c259ddbd` | label saving support | 6 | todo |
| 2022-08-13 | `58b584ab48d45c8348b3b4ad0f772df501b1e782` | multilingual variant display in admin | 5 | todo |
| 2022-08-16 | `144f9b53a556f65e1bd7906f0c63daf17f6a89c0` | todo cleanup; variant polish; folder sync started | 7 | todo |
| 2022-08-16 | `8ee708780a8262cc100fd0be99c41b7ab8c565a1` | initial subfolder sync | 5 | todo |
| 2022-08-16 | `d923bdd6258bad7227fbb88f02b8e1d7c0206918` | subfolder replication support; autoDialog updated for subfolders | 6 | todo |
| 2022-08-17 | `ec5819d31eee7cc4b1e3c5b93b1ada7485330668` | fixed replication issue | 1 | todo |
| 2022-09-02 | `792f3a21203abdecbe8310b202414aec57131bbb` | fixed issue with null width and/or height for video player component | 1 | todo |
| 2022-09-28 | `1911f571aed0278025032781062174adcd997fc9` | fixed issue with subfolder video activations | 1 | todo |
| 2022-09-29 | `4009329fbb7350f7dfbf6f3017cc55cbcf7c8c01` | bumped version number | 3 | obsolete (version/housekeeping) |
| 2023-04-04 | `f90d3b0f64a347d73db65e6e17b2cb540dbbaed2` | update to lodash dependency to support 6.5.10+ | 1 | todo |
| 2023-04-04 | `9cc8b91187738acb8aab765afc4492fb69c77fa4` | version updates to 6.0.2 | 3 | obsolete (version/housekeeping) |
| 2023-06-11 | `afa58e79146d39f3ac9869be8335a5893c9474fd` | fixes for playlist modal height and incorrect target logic on adding items to playlist | 2 | todo |
| 2023-06-14 | `0f1217091f952983e97d30fe28a04b7b63d7d860` | version bump to 6.0.3 | 3 | obsolete (version/housekeeping) |
| 2023-12-04 | `ccdaeb2fde4c2dcf19af42436e07ce97df8ef7cd` | fixed issue with full scroll import of assets not respecting AEM_NO_DAM tag filter | 2 | todo |
| 2023-12-04 | `f478496dc5a71e90c24b3fa3552150b35a88c9a0` | bump package versions | 3 | obsolete (version/housekeeping) |
| 2024-01-14 | `b4b122eb8c50547d83f94741bd82c825810c8c49` | BC-90 experience player fix | 4 | todo |
| 2024-01-14 | `308f42caed2e74ab7b3ce41ad193ce50ca875885` | added change event to switch account ID in experience player | 1 | todo |
| 2024-01-14 | `c7d573c6ad38e37badbe82ebba0466555c928c37` | bumped version in pom files | 3 | obsolete (version/housekeeping) |
| 2024-02-14 | `c100a16f1587f622be6825b1af291d038aad492d` | ContextPath Prefixed - Multiple Occurrences | 9 | todo |
| 2024-03-01 | `cf674b75ade8c509687deeb5187d6b14164effd4` | Falsy check added for all contextpath calls | 4 | todo |
| 2024-03-13 | `748bc76fbd172550f75344f2695f22be8566e847` | Adding in new way to sync from AEM to brightcove | 25 | todo |
| 2024-10-03 | `cdf5d26f1efd43cdbb674c09aa462bcafad37c33` | Fixing syncing issue with multiple files | 5 | todo |
| 2024-10-04 | `8ff0b48a50ca78beece748807b11efd7016db270` | updating version number | 3 | obsolete (version/housekeeping) |
| 2024-11-19 | `3b27f2f119a93b4d1d96928e667c5a85390ff9d0` | Adding ability to see all playlists | 5 | todo |
| 2024-11-20 | `48b94cc746414c9c1819b6ba0a1f3b1347297baf` | removing pagination on playlists | 5 | todo |
| 2024-11-21 | `bb0462da74913ddcc8e9dc780070083f41fb839d` | Updating Brightcove Metadata and logging | 5 | todo |
| 2025-02-13 | `d3743eac836e3c79791a6df4b8d575704e647bde` | Fixing proxy error on rendition creation | 4 | todo |
| 2025-08-04 | `04a2e70ea69c21c5f7a23b28bfa67229f78d2d2c` | Update BrightcoveExperiences.js | 1 | todo |
| 2026-02-13 | `86afcf2515f407840b48e7dc2c0bc6a6ded85481` | update versions to 6.0.12 | 3 | obsolete (version/housekeeping) |
