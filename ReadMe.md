# Polynote SQL Execution Fork

This repository contains a fork of [Polynote](https://github.com/polynote/polynote) that adds the capability to execute SQL queries. The original repository unfortunately lacks support for executing SQL code. Although there is an option to select an SQL interpreter, it does not function as intended. This fork aims to resolve that limitation by enabling SQL execution in AWS Redshift, with compatibility for PostgreSQL.

## Repository Structure

- **polynote-sql-compiled**: This folder contains the compiled version of Polynote with the added functionality for SQL execution.
- **polynote-sql-source**: This folder contains the forked source code of the original Polynote repository.

## Features

- **SQL Execution**: Execute SQL queries directly from Polynote notebooks.
- **AWS Redshift Support**: Seamless integration with AWS Redshift for data analysis.
- **PostgreSQL Compatibility**: The implementation is also compatible with PostgreSQL databases.

## Implementation Details

1. **SQL Interpreter Registration**: In the file `polynote-sql-source/polynote-kernel/src/main/scala/polynote/kernel/interpreter/`, the SQL interpreter has been registered by adding the entry `"sql" -> SQLInterpreter.Factory`.
   
2. **SQL Interpreter Class**: The SQL interpreter class has been implemented in `polynote-sql-source/polynote-kernel/src/main/scala/polynote/kernel/interpreter/python/SQLInterpreter.scala`. While the solution may not be the most elegant, it is functional and works as intended.

## Configuration

To set up the SQL connection, modify the `config.yml` file with the following block:

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

## Building from Source

You can build the project from source using [SBT](https://www.scala-sbt.org/). Follow the instructions in the official Polynote documentation for installation: [Polynote Installation Guide](https://polynote.org/latest/docs/installation/).

## Usage

1. Clone this repository.
2. Navigate to the `polynote-sql-compiled` folder and run the compiled Polynote.
3. Modify the `config.yml` file with your database connection details.
4. Launch Polynote and start executing SQL queries in your notebooks.

## Here are some screenshots showcasing the functionality of the Polynote SQL fork:

- **CLI Interface**:  
  ![CLI Interface](https://github.com/Easthy/polynote/blob/main/polynote-sql-compiled/cli.png)

- **Available Interpreters**:  
  ![Available Interpreters](https://github.com/Easthy/polynote/blob/main/polynote-sql-compiled/interpretators.png)

- **Matplotlib Visualization**:  
  ![Matplotlib Visualization](https://github.com/Easthy/polynote/blob/main/polynote-sql-compiled/matplotlib.png)

- **Plotly Visualization**:  
  ![Plotly Visualization](https://github.com/Easthy/polynote/blob/main/polynote-sql-compiled/plotly.png)

- **TkAgg Backend**:  
  ![TkAgg Backend](https://github.com/Easthy/polynote/blob/main/polynote-sql-compiled/tk_agg.png)

## Contributing

Contributions are welcome! If you have suggestions for improvements or encounter any issues, please feel free to open an issue or submit a pull request.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

---

Thank you for using this fork of Polynote! Enjoy executing your SQL queries!
