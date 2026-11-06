# 05 IPLD and content addressing

**English** | [日本語](05-ipld.ja.md)

Content addressing identifies data by what it contains rather than where it resides. Kotoba distinguishes identities for definitions, values, state, and artifacts so that stored or transferred content can be verified.

## From data to a CID

The [IPLD Data Model](https://ipld.io/docs/data-model/) describes data using kinds such as integers, strings, lists, maps, bytes, and links. A link identifies other content by CID. A codec defines a byte representation; [DAG-CBOR](https://ipld.io/specs/codecs/dag-cbor/spec/) is one such codec.

```text
logical value → contract-defined normalization → DAG-CBOR bytes
              → SHA-256 digest → CIDv1 including codec and hash information
```

This is a conceptual derivation. An implementation must also agree on versions, type tags, numeric precision, and link encoding. Printing EDN with `pr-str` and labeling it DAG-CBOR does not produce a DAG-CBOR block. Kotoba keywords and sets likewise need language codec rules; they are not primitive IPLD kinds themselves.

## Keep the identities separate

| Identity | Content | What changes it |
|---|---|---|
| Source CID or digest | Exact authored bytes | Whitespace and comments as well as other byte changes |
| Definition CID | A checked, normalized definition with six sealed inputs | Types, effects, dependencies, contracts, or normalized body |
| Artifact CID or digest | Generated target bytes | Compiler, target, ABI, optimization, layout, and related inputs |
| Value CID | A value normalized under its codec contract | The value or encoding contract |
| Admission CID | An object binding execution conditions | Grants and execution conditions; a DefCID alone is insufficient |

A generic content address for source is separate from the implementation status of a specific language source-tree identity contract. A hash of arbitrary source or SIR must not be relabeled a Definition CID.

## Nodes, blocks, and execution memory

One IPLD block can contain multiple nodes. Each expression element need not become a separate block or network request. Smaller blocks help sharing and partial retrieval; larger blocks can reduce link and lookup overhead. Access and update patterns determine the tradeoff.

IPLD does not prescribe CPU pointer layout. After verifying and hydrating a value into a local handle or contiguous buffer, execution need not compute a CID for every element access. A CID identifies content, a handle refers to a value within an execution, and a capability grants an operation. CID equality does not establish availability, confidentiality, or execution permission.

## Read the contracts

Read [physical costs and IPLD](../../lang/physical-model-and-ipld.md), the [value codec](../../../lang/value-codec.edn), and [code identity](../../../lang/code-identity.edn). The [CID and handle contract](../../adr/ADR-kotoba-cid-handle-cap-runtime.md) explains stored representations and execution layouts.

## Exercise and answer

If a definition has the same DefCID when compiled for ARM64 and x86-64, must its machine-code bytes agree? No. Definition identity binds the computation, while artifact identity binds a particular realization. Separating them allows shared meaning across different targets.

[Previous](04-checking-and-ir.md) · [Contents](README.md) · [Next: definition identity](06-definition-identity.md)
