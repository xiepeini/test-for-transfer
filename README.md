Panthera
=====================
# An open source SQL-92 extension for HiveQl #

#### Project contact: [Daoyuan Wang](mailto:daoyuan.wang@intel.com), [Liye Zhang](mailto:liye.zhang@intel.com), [Zhihui Li](mailto:zhihui.li@intel.com), [Grace Huang](mailto:jie.huang@intel.com), [Jiangang Duan](mailto:jiangang.duan@intel.com)
---
### OVERVIEW ###

This project is an independent version of ["Project Panthera"](<https://github.com/intel-hadoop/project-panthera-ase>).
This project will build a jar so that you can use direct parse queries like "select a from x where b > (select max(c) from y);" into Hive-0.12 runnable HiveQl,
output as HiveQl text or Hive AST.

---
### Feature List ###
 - 1. Support all Hive query syntax which is compatible SQL92.
 - 2. Base on 1, Panthera ASE support:

<table>
   <tr>
      <td>Feature</td>
      <td>Comment</td>
      <td>Example </td>
   </tr>
   <tr>
      <td>Multi-Table in FROM clause</td>
      <td></td>
      <td>select * from x,y where x.a=y.b </td>
   </tr>
   <tr>
      <td>Subquery in WHERE clause</td>
      <td>Not support non-equal joint condition </td>
      <td>select a from x where a = (select max(c) from y) </td>
   </tr>
   <tr>
      <td>Subquery in HAVING clause</td>
      <td>Not support non-equal joint condition </td>
      <td>select max(a) from x group by b having max(a) = (select max(c) from y) </td>
   </tr>
   <tr>
      <td>Order by column position</td>
      <td></td>
      <td>select a,b from x order by 1 </td>
   </tr>
   <tr>
      <td>Top level UNION ALL</td>
      <td></td>
      <td>select a from x union all select a from y </td>
   </tr>
</table>

 - You can also click [here](http://intel-hadoop.github.io/project-panthera-ase/) to see what ASE supports in detail.
