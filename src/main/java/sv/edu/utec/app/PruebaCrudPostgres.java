package sv.edu.utec.app;
import sv.edu.utec.datos.JdbcProductoRepositorio;
import sv.edu.utec.datos.ProductoRepositorio;
import sv.edu.utec.datos.RepositorioException;
import sv.edu.utec.modelo.Producto;
import java.util.List;
public class PruebaCrudPostgres {
    public static void main(String[] args) {
        try {
// La UNICA linea distinta a la PruebaCrud de la Guia 4
            ProductoRepositorio repo = new JdbcProductoRepositorio();
// 0. Limpieza: la prueba se puede ejecutar varias veces
            for (Producto p : DatosDemo.productos()) {
                repo.eliminar(p.getCodigo());
            }
            repo.eliminar("P-009");
            repo.eliminar("P-011");
            System.out.println("0) Limpieza lista");
// ---- PARTE C: CREATE y DELETE ----
            int n = repo.insertarVarios(DatosDemo.productos());
            System.out.println("1) Carga inicial: " + n + " productos");
            repo.insertar(new Producto("P-009", "Azucar blanca 1 kg",
                    "Abarrotes", 1.10, 150));
            System.out.println(" Insertado: P-009");
            try {
                repo.insertar(new Producto("P-001", "Duplicado", "Abarrotes", 1.00, 1));
            } catch (RepositorioException e) {
                System.out.println(" Rechazado por PostgreSQL: " + e.getMessage());
            }
            try {
                repo.insertar(new Producto("P-010", "Error", "Abarrotes", -5.00, 1));
            } catch (IllegalArgumentException e) {
                System.out.println(" Rechazado por Java: " + e.getMessage());
            }
            System.out.println("2) P-009 eliminado: " + repo.eliminar("P-009"));
            System.out.println(" P-009 eliminado otra vez: " + repo.eliminar("P-009"));
// ---- PARTE C: READ ----
            System.out.println("3) Buscar P-004:");
            repo.buscarPorCodigo("P-004").ifPresentOrElse(
                    p -> System.out.println(" " + p),
                    () -> System.out.println(" No existe"));
            System.out.println(" Buscar P-404: "
                    + repo.buscarPorCodigo("P-404").map(Producto::getNombre)
                    .orElse("No existe"));
            System.out.println(" Total de productos: " + repo.listarTodos().size());
            System.out.println(" Categoria Limpieza:");
            repo.listarPorCategoria("Limpieza")
                    .forEach(p -> System.out.println(" " + p));
            System.out.println(" Nombre contiene \"LB\":");
            repo.buscarPorNombre("LB")
                    .forEach(p -> System.out.println(" " + p));
            System.out.println(" Nombre contiene \"%\": "
                    + repo.buscarPorNombre("%").size() + " resultados");
            System.out.println(" Precio entre $1.00 y $2.00:");
            repo.buscarPorRangoDePrecio(1.00, 2.00)
                    .forEach(p -> System.out.println(" " + p));
// ---- Aqui agregara los bloques de las partes D y E ----
        } catch (RepositorioException e) {
            System.err.println("Error de datos: " + e.getMessage());
        }
    }
}