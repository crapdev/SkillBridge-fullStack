"""Reglas de calidad: cada regla devuelve las filas que la INCUMPLEN (vacío = todo bien)."""

from .ejemplo_completitud import filas_con_nulos
from .unicidad_y_dominio import ids_duplicados, estados_invalidos

__all__ = [
    "filas_con_nulos",
    "ids_duplicados",
    "estados_invalidos",
]
