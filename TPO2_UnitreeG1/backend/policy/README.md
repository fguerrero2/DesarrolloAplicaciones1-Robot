# Política de locomoción del G1

`motion.pt` es la política pre-entrenada (TorchScript) de
[unitree_rl_gym](https://github.com/unitreerobotics/unitree_rl_gym) (`deploy/pre_train/g1/motion.pt`,
commit `276801e`), distribuida bajo licencia BSD-3-Clause (ver `LICENSE-unitree_rl_gym`).

La usa `app/g1_dds.py` con la configuración de `deploy/deploy_real/configs/g1.yaml`:
observación de 47 valores, 12 acciones (piernas), 50 Hz.
