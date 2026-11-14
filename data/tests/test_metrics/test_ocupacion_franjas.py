"""Pruebas para la metrica de ocupacion de franjas por mentoria (HU-45)"""
from skillbridge_data.metrics.ocupacion_franjas import ocupacion_franjas

def test_calcula_ocupacion_de_franjas(tables):
  #Ejecutamos nuestra metrica pasandole la tabla de horarios de prueba
  resultado = ocupacion_franjas(tables["availability_slots"])

  #Convertimos las filas de Spark a un diccionario de Python para comparar facil:
  # { "id_mentoria": (total_franjas, franjas_reservadas, porcentaje) }
  filas = {
    fila["offering_id"]: (
      fila["franjas"],
      fila["reservadas"],
      fila["porcentaje_ocupacion"],
    )
    for fila in resultado.collect()
  }

  # Verificamos que coincida exactamente con los valores esperados en la HU
  assert filas == {
    "b0000000-0000-0000-0000-000000000001": (6, 5, 83.33),
    "b0000000-0000-0000-0000-000000000002": (4, 2, 50.0),
    "b0000000-0000-0000-0000-000000000003": (4, 3, 75.0),
  }

def test_columnas_de_salida_ocupacion_franjas(tables):
  #Comprobamos que la tabla devuelta tenga exactamente las 4 columnas pedidas
  resultado = ocupacion_franjas(tables["availability_slots"])
  assert resultado.columns == [
    "offering_id",
    "franjas",
    "reservadas",
    "porcentaje_ocupacion",
  ]

