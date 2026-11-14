English · [简体中文](../../zh/modelcatalog/model-services.md)

# Model configuration constraints

Reviewed on 2026-11-14 for developers.

This document holds the **operational constraints and recipes** for the unified model service configuration rebuild (implemented 2026-11-14): terminology and scope, domain model rules, storage rules, adapter dispatch rules, and the mandatory steps when adding a vendor, provider tool, or speech adapter. Follow this document for future development.

## Terminology and scope

- **Model service**: an independently named configuration that provides access to selected models. Multiple model services can share a provider or compatible protocol while keeping their credentials, connections, and model selections independent.
- **Built-in provider template**: a predefined provider configuration used to create a model service, describing the capabilities integrated by the application and their setup defaults.
- **Service credential**: access information owned by a model service and used by one or more of its connections; a connection with different access requirements can use an independent credential.
- **Service connection**: the access configuration for one integrated interface of a model service — protocol, endpoint, credential source, and required access parameters.
- **LLM model**: a model configured for the application's conversational generation tasks, covering ordinary conversation and the fast-model role.
- **Speech model**: a model configured for speech recognition, audio understanding, speech synthesis, or realtime voice conversation; one model can support multiple uses.
- **Custom LLM integration**: a user-configured third-party LLM connection through OpenAI Chat Completions or Anthropic Messages, with connection settings and conversational models; its scope excludes provider-specific tools and speech capabilities.

## Domain model constraints

### Instance and template identity are separate

> Decision rationale: a single shared provider identity would simplify provider grouping but prevent independent accounts and compatible third-party access; therefore every configuration owns an independent identity and name, and model selections and service operations must distinguish the configured instance from its provider template.


- A configured instance (`ModelServiceInstance`) owns a stable ID; `templateId` is adapter metadata only (protocol dispatch, vendor tool matrix, speech adapters, brand icons). **Never** use an instance ID as a vendor identifier — brand icons resolve by template ID, and an instance ID silently falls back to the placeholder icon (already bitten once).
- `ModelSelection = (instanceId, connectionId, modelId)`. Defaults, session persistence, and the codec (`ModelSelectionCodec`, three-part format) all start from the instance ID; identically named models in different instances never conflate.
- Custom LLM services have `templateId == null` and exactly one conversation connection. The vendor tool matrix is naturally unavailable to them, while MCP/plugin tools keep working.

### Connections and capability categories

- One `ServiceConnection` maps to exactly one `ServiceCapability` (Conversation / Recognition / Synthesis / Realtime). Never merge them.
- A recognition connection hosts both dedicated transcription (STT) and audio understanding (ASR) models; protocol dispatch follows each model's declared capabilities, never a blanket per-instance check.
- Dual-protocol vendors (OpenAI-compatible + Anthropic-compatible) must declare **both protocols' default endpoints** in their template connection (MiniMax / MiMo once missed the Anthropic path, breaking the protocol-switch endpoint reset).

### Credential convergence

- `List<ModelServiceInstance>.resolveConnection(selection)` (domain top-level extension) is the **single source of truth** for runtime and UI snapshot resolution: paused, blank-credential, missing connection, or missing model resolves to null; the first key of a comma-separated credential wins. The repository and every consumer delegate to it — do not resolve manually.
- A credential source is either shared (instance `sharedApiKey`) or a per-connection `CredentialSource.Independent`. Vendor-tool credentials belong to the instance's **conversation connection**; cross-model tools resolve the source instance credential at execution time.

### Drafts and enable state

- `ModelServiceDraft` carries no enable state: pausing and resuming commit independently, and a draft save reads the latest stored enable state so it cannot overwrite an immediate toggle.
- Save validation (`hasValidConfiguration`): at least one connection has selected models; every connection with selected models is structurally complete (protocol, endpoint, independent credential non-blank); the workspace ID is validated only when the template supports it. Connections without selected models do not gate saving.
- `EndpointPolicy.VendorManaged` endpoints snap back to the template default on save and are not user-editable.
- After a successful save the ViewModel must **adopt the saved state in place** (instance ID, normalized connections) and must never push another detail destination onto the nav stack (this once caused stacked-back and dead-click bugs); effect streams use `MutableSharedFlow(replay=0)`, never a replaying Channel.

## Storage constraints

- Room (`model_template`, version 3) holds only the public template catalog: vendor templates, capability connection definitions, built-in candidate catalogs. Startup runs seeding, pruning of retired templates, and built-in catalog merging — all idempotent.
- Instances, connection credentials, shared/independent sources, selected models, and default assignments live in one Keystore-encrypted snapshot (`model_service_configuration_v1`); every mutation **commits the whole snapshot atomically**, publishes only after success, and keeps the previous state plus the draft on failure.
- Default assignments (assistant/fast/recognition/synthesis/realtime + voices) are unencrypted keys in the same file. Deleting an instance must clear the referencing default assignments and bound voices **in the same commit** (reading voice resets to unset; call voice override is cleared).
- The reading (cascade TTS) voice is a single global key where **blank means unset**. Switching the synthesis model clears it, so the voice never outlives the service it was picked for. The defaults page auto-fills a blank voice with the first voice of the newly loaded model's list (writes only blanks — user-configured voices are never rewritten, since voice catalogs are not closed sets: base and cloned voices live outside them). At request time the synthesis repository also resolves the effective voice as a safety net: a configured voice wins, an unset voice falls back to `VoiceCatalogResolver.builtInDefaultVoice` (the first built-in voice for the service, narrowed by model for Qwen whose table is per-model), and a service with no built-in table (openaicompatible vendors) sends blank and lets the server decide. Never send a vendor-specific placeholder voice id to another vendor (MiniMax rejects it with error 2054 mid-call).
- Per the repository's development-stage rule, **no historical migrations**: changing the config format means bumping the prefs file-name version (vN → vN+1); Room bumps its version with `fallbackToDestructiveMigration`. Default-assignment key names stay stable.

## Adapter and runtime dispatch

- Chat protocol dispatch converges in `AgentLLMModelFactory.createModel`'s `when(baseType)`; a new protocol requires: an `ApiProtocol` entry → a factory branch → template connection `protocols`/`defaultEndpoints` allowing it.
- Speech adapters register by **template ID** (`CapabilityAdapter.serviceIds`); an unresolvable `VoiceCapabilityRegistry` lookup is an explicit failure and must never fall back to another vendor's protocol. Connection tests: conversation connections probe the model catalog; speech connections probe a template handshake (unimplemented probes report Unsupported).
- Realtime assembly dispatches by `templateId` (current branches: qwen / openai); templates without a native adapter must not appear as Realtime candidates.
- The vendor tool matrix lives in `OfficialToolSupport.all`, matched by `templateId + protocol + model family`; `ModelConfig` / `ModelRuntimeMetadata` carry `templateId` and round-trip through RunConfig metadata (`gimi.model.template_id`); the DeepSeek Anthropic reference tool-loading mode keys on template identity plus the official endpoint.
- Endpoint strategy: `VendorManaged` endpoints (Bailian WebSocket, OpenAI Realtime) come from the template; everything else uses the configured endpoint. Speech request base URLs converge in `speechRequestBaseUrl` (Bailian workspace IDs rewrite to workspace-specific domains).

## Recipes

### Add a vendor template

1. Append a template in `ModelServiceTemplates` (`data/modelcatalog`): capability connections, `catalog` candidates; **dual-protocol vendors must also declare the Anthropic address in `additionalEndpoints`**.
2. Map the template ID → icon in `LLMModelServiceIcon` (`core/designsystem`); a missing entry silently falls back to the placeholder icon (Qwen was bitten).
3. If the vendor ships tools, add an `OfficialToolSpec` to domain `OfficialToolSupport.all` (see below).
4. Add the tool-row copy per toolId in `feature/modelsettings/detail/OfficialToolCopy.kt`.
5. For speech capabilities see "Add a speech adapter".
6. `feature/modelsettings` strings need no change (template names and capability summaries are template-driven).

### Add a provider tool

1. Append an `OfficialToolSpec` to domain `OfficialToolSupport.all`: `templateId`, allowed `protocols`, `binding` (ProviderDeclaration / ProviderNative / LocalFunctions), `canBeUsedAcrossModels`, optional model-family restriction.
2. Add a LocalFunctions construction branch in `data/agent`'s `OfficialToolFactory`: resolve the credential from the source instance's conversation connection — never hard-code a vendor key.
3. Implement the independent API / tool class in `data/modelaccess`.
4. Add the name-and-function copy per toolId in `feature/modelsettings`'s `OfficialToolCopy` (the UI shows neither the catalog's English name nor a vendor prefix).

### Add a speech adapter

1. Implement a `CapabilityAdapter` in `data/modelaccess` (`serviceIds` = template ID, `endpointStrategy`, declared capabilities) plus its protocol implementation.
2. Register it via `@IntoSet` in `data/modelaccess`'s `ModelAccessModule`.
3. Sync the hand-mirrored checklist in `app`'s `VoiceCapabilityCatalogConsistencyTest` — every voice-capable model declared by the template catalog must resolve to an adapter or that test fails.

### Change the configuration storage

1. Bump the encrypted snapshot prefs file-name version; no migrations; keep default-assignment key names.
2. For Room schema changes bump the version (currently 3) relying on destructive rebuild; keep seeding/pruning idempotent.
3. `ModelServiceConfigurationPersistenceTest` must cover: failed commit publishes nothing, concurrent enable/save consistency, deletion clearing defaults and voices, corrupt data falling back to an empty configuration.
