# Sideload signing key

`haire-sideload.jks` (store/key password `haire-alpha`, alias `haire`) signs the debug and sideload
release builds so that every build — from any machine or CI — installs as an *update* over the
previous one without uninstalling (which would otherwise wipe settings).

It is intentionally public: it identifies sideloaded builds only. Do **not** use it for a Play Store
release; create a private upload key for that and keep it out of the repository.
