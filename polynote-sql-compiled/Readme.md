# Polynote SQL Interpreter Fork

This repository is a fork of [Polynote](https://github.com/polynote/polynote). The original repository unfortunately lacks support for executing SQL code. While there is an option to select an SQL interpreter, it does not function as expected. This fork addresses that limitation by adding the capability to execute SQL commands in AWS Redshift, and it should also work with PostgreSQL.

## Features

- **SQL Execution**: Execute SQL queries directly from Polynote notebooks.
- **AWS Redshift Support**: Seamless integration with AWS Redshift for data analysis.
- **PostgreSQL Compatibility**: The implementation is compatible with PostgreSQL databases.

## Implementation Details

In this fork, the following modifications have been made:

1. **SQL Interpreter Registration**: In the file `polynote-kernel/src/main/scala/polynote/kernel/interpreter/`, I registered the SQL interpreter by adding the entry `"sql" -> SQLInterpreter.Factory`.
   
2. **SQL Interpreter Class**: I created the SQL interpreter class in `polynote-kernel/src/main/scala/polynote/kernel/interpreter/python/SQLInterpreter.scala`. While the solution may not be elegant, it is functional and works as intended.

## Configuration

To set up the SQL connection, you need to modify the `config.yml` file. Add the following block to configure your database connection:

```yaml
env:
  sql_drivername: "postgresql+psycopg2"
  sql_user: "db_username"
  sql_password: "db_username_password"
  sql_host: "db_host"
  sql_port: db_port_integer
  sql_database: "db_name"
```

Make sure to replace the placeholder values with your actual database credentials.

## Usage

1. Clone this repository.
2. Modify the `config.yml` file with your database connection details.
3. Launch Polynote and start executing SQL queries in your notebooks.

## Contributing

Contributions are welcome! If you have suggestions for improvements or encounter any issues, please feel free to open an issue or submit a pull request.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

---

Thank you for using this fork of Polynote! Happy querying! 
