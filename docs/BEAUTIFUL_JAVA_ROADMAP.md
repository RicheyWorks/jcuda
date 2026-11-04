# Beautiful Java milestones

Richmond's sequence is **20 distinct verified fixes → contributor report → original Java art powered by JCuda**. Current work remains focused on fixes. See the [verified inventory](CONTRIBUTION_INVENTORY.md); none of the ideas below counts as an implemented fix or finished demo.

## The 20-fix report

Build the report from the inventory and refresh every PR's state. Explain the concrete failures, changes, tests, hardware/build caveats and ways others can help. Distinguish unmerged drafts from upstream acceptance and releases. Include copyable setup steps and starter scopes, while preserving the original maintainer's ownership and review decisions.

## Art ideas to revisit afterward

1. **Reaction-diffusion prints.** Java controls a seeded Gray–Scott simulation and palette; CUDA kernels evolve two grid fields. Render reproducible PNGs and a short evolution sequence. Check a small grid against a CPU reference with an explicit numeric tolerance.
2. **Orbit-trap fractal atlas.** Java provides framing, palette and exploration controls; CUDA computes escape iterations and orbit-trap values per pixel. Save the seed/viewport/precision settings and demonstrate the GPU-produced scalar field separately from Java's display work.
3. **Particle-flow drawings.** Java authors seeded vector fields and composition parameters; CUDA updates particle trajectories. Export accumulated trails as original artwork, with a deterministic small CPU reference for one update step and clearly stated floating-point limits.

## Evidence required for an actual GPU demo

- Keep original Java orchestration, CUDA kernel sources and build/run commands in a reviewable repository.
- Record seed, grid/particle size, parameters, kernel-source hash, Java/JCuda/CUDA/driver versions and device name alongside each exported work.
- Actually launch the kernels and retrieve GPU-computed data. A Java renderer, static image or synthetic animation alone is not proof of JCuda computation.
- Compare a small reference case with CPU results; save checksums or numeric summaries before rendering so correctness is independently inspectable.
- Measure device computation with CUDA events and report end-to-end time separately, including compilation, transfers and display/export. Warm up both paths and use matched workloads before making any speedup claim.
- Bound memory use, clean up resources, and coordinate any needed GPU-workload pause at a saved boundary. Resume and verify the workload afterward. Never reset the device or alter another project's saved assets.

These are candidate designs only. No demo has been built or benchmarked, and no performance claim is being made.
